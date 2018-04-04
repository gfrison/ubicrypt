/*
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 *
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.core;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.local.OnNewLocal;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.util.InProgressTracker;
import ubicrypt.core.util.QueueLiner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
  loader = AnnotationConfigContextLoader.class,
  classes = {FileCommanderIT.Config.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FileCommanderIT implements ApplicationContextAware {

  private static final Logger log = LoggerFactory.getLogger(FileCommanderIT.class);

  @Inject FileCommander fileCommander;
  @Inject LocalRepository localRepository;
  @Inject LocalConfig localConfig;
  @Inject IPGPService ipgpService;
  @Inject PublishSubject<ProgressFile> progressEvents;
  @Inject ProviderLifeCycle providerLifeCycle;

  @Resource
  @Qualifier("providerEvent")
  Observable<ProviderEvent> providerEvents;

  private ApplicationContext ctx;

  @Before
  public void setUp() throws Exception {
    providerEvents
        .filter(status -> status.getEvent() == ProviderStatus.active)
        .subscribeOn(Schedulers.io())
        .toBlocking()
        .first();
    Files.createDirectories(localRepository.getBasePath());
    LogManager.getLogger(QueueLiner.class).setLevel(Level.TRACE);
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteR(localRepository.getBasePath());
    TestUtils.deleteDirs();
  }

  @Test
  public void addFile() throws Exception {
    final String name = RandomStringUtils.randomAlphabetic(3);
    Utils.write(localRepository.getBasePath().resolve(name), "ciao".getBytes()).toBlocking().last();
    assertThat(
            fileCommander
                .addFile(localRepository.getBasePath().resolve(name))
                .toBlocking()
                .last()
                .getT2()
                .toBlocking()
                .single())
        .isTrue();
    final LocalFile file =
        localConfig
            .getLocalFiles()
            .stream()
            .filter(ffile -> ffile.getPath().equals(Paths.get(name)))
            .findFirst()
            .get();
    assertThat(file.getPath()).isEqualTo(Paths.get(name));
    assertThat(
            providerLifeCycle
                .enabledProviders()
                .get(0)
                .getRepository()
                .get(file)
                .map(Utils.is2string)
                .toBlocking()
                .single())
        .contains("ciao");
  }

  @Test
  public void removeFile() throws Exception {
    addFile();
    assertThat(localConfig.getLocalFiles().iterator().next().isRemoved()).isFalse();
    fileCommander
        .removeFile(
            localRepository
                .getBasePath()
                .resolve(localConfig.getLocalFiles().iterator().next().getPath()))
        .toBlocking()
        .singleOrDefault(null);
    assertThat(localConfig.getLocalFiles().iterator().next().isRemoved()).isTrue();
    ProviderHook hook = providerLifeCycle.currentlyActiveProviders().get(0);
    CountDownLatch cd = new CountDownLatch(1);
    AtomicReference<RemoteConfig> rconfig = new AtomicReference<>();
    hook.getAcquirer()
        .call(
            new Subscriber<AcquirerReleaser>() {
              @Override
              public void onCompleted() {}

              @Override
              public void onError(Throwable e) {}

              @Override
              public void onNext(AcquirerReleaser acquirerReleaser) {
                rconfig.set(acquirerReleaser.getRemoteConfig());
                cd.countDown();
              }
            });
    assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
    assertThat(rconfig.get().getRemoteFiles().iterator().next().isRemoved()).isTrue();
  }

  @Test
  public void deleteFile() throws Exception {
    addFile();
    fileCommander
        .deleteFile(
            localRepository
                .getBasePath()
                .resolve(localConfig.getLocalFiles().iterator().next().getPath()))
        .toBlocking()
        .lastOrDefault(null);
    assertThat(localConfig.getLocalFiles().iterator().next().isDeleted()).isTrue();
  }

  @Test
  public void updateFile() throws Exception {
    addFile();
    final Path path = Paths.get(localConfig.getLocalFiles().iterator().next().getName());
    Utils.write(localRepository.getBasePath().resolve(path), "ciao2".getBytes())
        .toBlocking()
        .last();
    Thread.sleep(100);
    final ArrayList<ProgressFile> progresses = new ArrayList<>();
    final Subscription sub = progressEvents.subscribe(progresses::add);
    Iterable<Boolean> list =
        fileCommander
            .updateFile(localRepository.getBasePath().resolve(path))
            .toBlocking()
            .toIterable();
    list = Lists.newArrayList(list);
    assertThat(list).hasSize(1);
    assertThat(list.iterator().next()).isTrue();
    assertThat(progresses).hasSize(2);
    assertThat(progresses.get(0).getChunk()).isEqualTo(5L);
    assertThat(progresses.get(1).isCompleted()).isTrue();
    assertThat(
            providerLifeCycle
                .enabledProviders()
                .get(0)
                .getRepository()
                .get(localConfig.getLocalFiles().iterator().next())
                .map(Utils.is2string)
                .toBlocking()
                .last())
        .contains("ciao2");
    sub.unsubscribe();
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    this.ctx = applicationContext;
  }

  @Configuration
  @Import({BaseConf.class})
  public static class Config {

    @Bean
    public Subject<ProviderEvent, ProviderEvent> providerEvent() {
      return ReplaySubject.create();
    }

    @Bean
    public Subject<Object, Object> appEvents() {
      return PublishSubject.create();
    }

    @Bean
    public ProviderLifeCycle providerLifeCycle() {
      return new ProviderLifeCycle();
    }

    @Bean
    public InProgressTracker inProgressTracker() {
      return new InProgressTracker();
    }

    @Bean
    PGPKeyPair keyPair() {
      return PGPEC.encryptionKey();
    }

    @Bean
    public List<ProviderHook> providers() {
      return new ArrayList<>();
    }

    @Bean
    public FileCommander fileCommander(final Path rndPath) {
      return new FileCommander(rndPath);
    }

    @Bean
    public LocalConfig localConfig() {
      return new LocalConfig() {
        {
          getProviders().add(TestUtils.fileProvider(TestUtils.tmp));
        }
      };
    }

    @Bean
    public Path rndPath() {
      final String rnd = RandomStringUtils.randomAlphabetic(3);
      final Path path = TestUtils.tmp.resolve(rnd);
      log.debug("created folder:{} \n", path);
      try {
        Files.createDirectories(path);
      } catch (IOException e) {
        Throwables.propagate(e);
      }
      return path;
    }

    @Bean
    public OnNewLocal onNewFileLocal() {
      return new OnNewLocal();
    }

    @Bean
    public LocalRepository localRepository(final Path rndPath) throws IOException {
      return new LocalRepository(rndPath);
    }

    @Bean
    public PublishSubject<ProgressFile> progressEvents() {
      return PublishSubject.create();
    }

    @Bean
    public Subject<FileEvent, FileEvent> fileEvents() {
      return PublishSubject.create();
    }

    @Bean
    public Subject<UbiFile, UbiFile> conflictEvents() {
      return PublishSubject.create();
    }

    @Bean
    public int deviceId(Path basePath) {
      return Utils.deviceId();
    }

    @Bean
    public PGPService pgpService() {
      return new PGPService();
    }

    @Bean
    public QueueLiner queueLiner(
        @Value("${saveConfIntervalMs:10000}") final Long saveConfIntervalMs) {
      return new QueueLiner(saveConfIntervalMs);
    }
  }
}
