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

import org.apache.commons.io.IOUtils;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.ByteArrayInputStream;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.zip.DeflaterInputStream;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.RemoteCtxConf;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.util.Persist;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
  loader = AnnotationConfigContextLoader.class,
  classes = {InitFileSyncronizerIT.Config.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class InitFileSyncronizerIT implements ApplicationContextAware {
  @Resource
  @Qualifier("providerEvent")
  Subject<ProviderEvent, ProviderEvent> providerStatusEvents;

  @Inject LocalConfig localConfig;
  @Inject LocalRepository localRepository;
  @Inject InitFileSyncronizer syncronizer;
  @Inject IPGPService ipgpService;
  int deviceId = Utils.deviceId();
  private ApplicationContext ctx;

  @Before
  public void setUp() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.createDirs();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
  }

  @Test
  public void local2Remote() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    final LocalFile origin =
        new LocalFile() {
          {
            setPath(Paths.get("origin"));
          }
        };
    localConfig.getLocalFiles().add(origin);

    final CountDownLatch cd = new CountDownLatch(1);
    syncronizer.setOnComplete(cd::countDown);
    final FileProvider fp1 = TestUtils.fileProvider(TestUtils.tmp2);
    final ProviderHook hk1 = ctx.getBean(ProviderHook.class, fp1);

    assertThat(cd.await(4, SECONDS)).isTrue();
    assertThat(IOUtils.readLines(hk1.getRepository().get(origin).toBlocking().last()))
        .contains("ciao");
  }

  @Test
  public void local2Remote2Providers() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    final LocalFile origin =
        new LocalFile() {
          {
            setPath(Paths.get("origin"));
          }
        };
    localConfig.getLocalFiles().add(origin);

    final CountDownLatch cd = new CountDownLatch(1);
    syncronizer.setOnComplete(cd::countDown);
    final FileProvider fp1 = TestUtils.fileProvider(TestUtils.tmp);
    final FileProvider fp2 = TestUtils.fileProvider(TestUtils.tmp2);
    final ProviderHook hk1 = ctx.getBean(ProviderHook.class, fp1);
    final ProviderHook hk2 = ctx.getBean(ProviderHook.class, fp2);

    assertThat(cd.await(4, SECONDS)).isTrue();
    Thread.sleep(100);
    assertThat(IOUtils.readLines(hk1.getRepository().get(origin).toBlocking().first()))
        .contains("ciao");
    assertThat(IOUtils.readLines(hk2.getRepository().get(origin).toBlocking().first()))
        .contains("ciao");
  }

  @Test
  public void local2RemoteRemoved() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    final LocalFile origin =
        new LocalFile() {
          {
            setPath(Paths.get("origin"));
            setRemoved(true);
          }
        };
    localConfig.getLocalFiles().add(origin);

    final CountDownLatch cd = new CountDownLatch(1);
    syncronizer.setOnComplete(cd::countDown);
    final FileProvider fp1 = TestUtils.fileProvider(TestUtils.tmp2);
    final ProviderHook hk1 = ctx.getBean(ProviderHook.class, fp1);

    assertThat(cd.await(4, SECONDS)).isTrue();
  }

  @Test
  public void remote2Local() throws Exception {
    final RemoteFile remoteFile =
        new RemoteFile() {
          {
            setRemoteName("origin");
          }
        };
    final CountDownLatch cd = new CountDownLatch(1);
    syncronizer.setOnComplete(cd::countDown);
    final FileProvider fp1 = TestUtils.fileProvider(TestUtils.tmp2);
    fp1.put(
            "origin",
            AESGCM.encryptIs(
                remoteFile.getKey().getBytes(),
                new DeflaterInputStream(new ByteArrayInputStream("ciao".getBytes()))))
        .toBlocking()
        .last();
    final Persist os =
        new Persist(fp1) {
          {
            setPgpService(ipgpService);
          }
        };
    os.putObject(
            new RemoteConfig() {
              {
                getRemoteFiles().add(remoteFile);
              }
            },
            fp1.getConfFile())
        .toBlocking()
        .last();
    final ProviderHook hk1 = ctx.getBean(ProviderHook.class, fp1);
    assertThat(cd.await(4, SECONDS)).isTrue();
    assertThat(localConfig.getLocalFiles()).hasSize(1);
  }

  @Test
  public void remote2localRemoved() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    final LocalFile origin =
        new LocalFile() {
          {
            setPath(Paths.get("origin"));
            getVclock().increment(deviceId);
          }
        };
    localConfig.getLocalFiles().add(origin);

    final RemoteFile remoteFile =
        new RemoteFile() {
          {
            setId(origin.getId());
            setRemoteName("origin");
            setPath(Paths.get("origin"));
            setRemoved(true);
            getVclock().increment(deviceId);
            getVclock().increment(deviceId);
          }
        };
    final CountDownLatch cd = new CountDownLatch(1);
    syncronizer.setOnComplete(cd::countDown);
    final FileProvider fp1 = TestUtils.fileProvider(TestUtils.tmp2);
    final Persist os =
        new Persist(fp1) {
          {
            setPgpService(ipgpService);
          }
        };
    os.putObject(
            new RemoteConfig() {
              {
                getRemoteFiles().add(remoteFile);
              }
            },
            fp1.getConfFile())
        .toBlocking()
        .last();
    final ProviderHook hk1 = ctx.getBean(ProviderHook.class, fp1);

    assertThat(cd.await(4, SECONDS)).isTrue();
    assertThat(localConfig.getLocalFiles()).hasSize(1);
    assertThat(localConfig.getLocalFiles().iterator().next().isRemoved()).isTrue();
  }

  @Override
  public void setApplicationContext(final ApplicationContext applicationContext)
      throws BeansException {
    this.ctx = applicationContext;
  }

  @Configuration
  @Import(RemoteCtxConf.class)
  public static class Config {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigIn() {
      return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public Subject<Boolean, Boolean> synchProcessing() {
      return PublishSubject.create();
    }

    @Bean
    public LocalConfig localConfig() {
      return new LocalConfig();
    }

    @Bean
    public InitFileSyncronizer initFileSyncronizer() {
      return new InitFileSyncronizer();
    }

    @Bean
    public LocalRepository localRepository() {
      return new LocalRepository(TestUtils.tmp);
    }

    @Bean
    public IPGPService pgpService() {
      return new PGPService();
    }

    @Bean
    public PGPKeyPair keyPair() {
      return PGPEC.encryptionKey();
    }

    @Bean
    public int deviceId() {
      return Utils.deviceId();
    }
  }
}
