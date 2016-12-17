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

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.Subscription;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.events.ShutdownOK;
import ubicrypt.core.events.ShutdownRequest;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.gdrive.GDriveConf;
import ubicrypt.core.provider.gdrive.GDriveProvider;
import ubicrypt.core.provider.s3.S3Provider;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {UbiConf.class, GDriveDownloadIT.Conf.class},
  initializers = {GDriveDownloadIT.KeyInitializer.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ConditionalOnProperty(name = "user.name", havingValue = "gfrison")
public class GDriveDownloadIT {
  private static final Logger log = getLogger(GDriveDownloadIT.class);

  private static GDriveConf conf;
  private static int nfiles = 10;
  private static int size = 1 << 16;
  private static Set<UbiProvider> providers = new HashSet<>();

  @Inject ProviderCommander providerCommander;
  @Inject FileFacade fileFacade;

  @Resource
  @Qualifier("providerEvent")
  Observable<ProviderEvent> providerEvent;

  @Inject ProviderLifeCycle providerLifeCycle;
  @Inject Path basePath;

  @Resource
  @Qualifier("fileEvents")
  Observable<FileEvent> fileEvents;

  @Inject LocalConfig localConfig;

  @Resource
  @Qualifier("appEvents")
  private Observable<Object> appEvents;

  @BeforeClass
  public static void createConfig() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.createDirs();
    conf = TestUtils.createGDriveConf();
    uploadFiles(new GDriveProvider(conf));
  }

  public static void uploadFiles(final UbiProvider provider)
      throws GeneralSecurityException, IOException {
    final SpringApplication app = new SpringApplication(UbiConf.class, TestPathConf.class);
    app.setRegisterShutdownHook(false);
    app.addInitializers(new TestKeyInitializer());
    app.setLogStartupInfo(false);
    app.setBannerMode(Banner.Mode.OFF);
    ConfigurableApplicationContext ctx = app.run();
    FileFacade fileFacade = ctx.getBean(FileFacade.class);
    ProviderCommander providerCommander = ctx.getBean(ProviderCommander.class);
    assertThat(providerCommander.register(provider).toBlocking().last()).isTrue();

    //create and track files
    List<Observable<Boolean>> ls =
        IntStream.range(0, nfiles)
            .mapToObj(
                i -> TestUtils.createRandomFile(TestUtils.tmp.resolve(String.valueOf(i)), size))
            .map(fileFacade::addFile)
            .map(ob -> ob.toBlocking().first())
            .map(Tuple2::getT2)
            .collect(Collectors.toList());
    assertThat(Observable.merge(ls).toBlocking().last()).isTrue();
    providers = ctx.getBean(LocalConfig.class).getProviders();

    //remove lock gdrive
    Subject<Object, Object> appEvents = ctx.getBean("appEvents", Subject.class);
    CountDownLatch cd = new CountDownLatch(1);
    appEvents.filter(event -> event instanceof ShutdownOK).subscribe(next -> cd.countDown());
    appEvents.onNext(new ShutdownRequest());
    try {
      if (cd.await(10, SECONDS)) {
        log.info("shutting gracefully down");
      } else {
        log.info("shutting process timed out");
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    ctx.close();
    log.info("upload complete");
  }

  @Before
  public void init() throws GeneralSecurityException, IOException {
    TestUtils.deleteDirs();
    TestUtils.createDirs();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
  }

  @Test
  public void downloadFiles() throws Exception {
    CountDownLatch cd = new CountDownLatch(1);
    Subscription sub =
        appEvents.subscribe(
            event -> {
              if (event instanceof SynchDoneEvent) {
                cd.countDown();
              }
            });
    assertThat(cd.await(20, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();
    assertThat(Files.list(TestUtils.tmp2)).hasSize(nfiles);
    GDriveProvider provider =
        (GDriveProvider) providerLifeCycle.currentlyActiveProviders().get(0).getProvider();
    provider.getDrive().files().delete(conf.getFolderId()).execute();
  }

  @Configuration
  public static class Conf {
    @Bean
    public Path basePath(@Value("${home:@null}") final String home) {
      return TestUtils.tmp2;
    }

    /** providers */
    @Bean
    public List<ProviderDescriptor> providerDescriptors() {
      return ImmutableList.of(
          new ProviderDescriptor(FileProvider.class, "file", "local folder", null),
          new ProviderDescriptor(S3Provider.class, "s3", "Amazon S3", null),
          new ProviderDescriptor(GDriveProvider.class, "gdrive", "Google Drive", null));
    }

    @Bean
    public LocalConfig localConfig() {
      LocalConfig config = new LocalConfig();
      config.setProviders(providers);
      return config;
    }
  }

  public static class KeyInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
      applicationContext.getBeanFactory().registerSingleton("keyPair", TestKeyInitializer.keyPair);
      applicationContext.getBeanFactory().registerSingleton("signKey", TestKeyInitializer.signKey);
    }
  }
}
