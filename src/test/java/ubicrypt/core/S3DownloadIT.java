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

import com.amazonaws.regions.Regions;

import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.Subscription;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.provider.s3.S3Conf;
import ubicrypt.core.provider.s3.S3Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.GDriveDownloadIT.uploadFiles;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {UbiConf.class, GDriveDownloadIT.Conf.class},
  initializers = {GDriveDownloadIT.KeyInitializer.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ConditionalOnProperty(name = "aws.accessKey")
public class S3DownloadIT {
  private static int nfiles = 10;
  private static int size = 1 << 16;
  private static Set<UbiProvider> providers;
  private static S3Provider provider;

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
    if (StringUtils.isEmpty(System.getenv("aws.accessKey"))) {
      return;
    }
    provider =
        new S3Provider() {
          {
            setConf(
                new S3Conf() {
                  {
                    setAccessKeyId(System.getenv("aws.accessKey"));
                    setSecrectKey(System.getenv("aws.secret"));
                    setRegion(Regions.EU_CENTRAL_1);
                    setBucket("test-gfrison");
                  }
                });
          }
        };
    uploadFiles(provider);
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
    if (StringUtils.isEmpty(System.getenv("aws.accessKey"))) {
      return;
    }
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
    S3Provider provider =
        (S3Provider) providerLifeCycle.currentlyActiveProviders().get(0).getProvider();
    provider
        .getClient()
        .listObjects("test-gfrison")
        .getObjectSummaries()
        .stream()
        .forEach(
            s3ObjectSummary ->
                provider.getClient().deleteObject("test-gfrison", s3ObjectSummary.getKey()));
  }
}
