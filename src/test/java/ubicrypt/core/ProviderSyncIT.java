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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.util.function.Tuple2;
import rx.Observable;
import rx.Subscription;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {UbiConf.class, TestPathConf.class},
  initializers = {TestKeyInitializer.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class ProviderSyncIT {
  public static final Path tmp3 = Paths.get(System.getProperty("java.io.tmpdir")).resolve("ubiq3");

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

  @Before
  public void setUp() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.deleteR(tmp3);
    TestUtils.createDirs();
    Files.createDirectories(tmp3);
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.deleteR(tmp3);
  }

  public void addProvider(UbiProvider provider) {
    assertThat(providerCommander.register(provider).toBlocking().last()).isTrue();
  }

  @Test
  public void syncProvider() throws Exception {
    addProvider(TestUtils.fileProvider(TestUtils.tmp2));
    //create 20 files
    List<Observable<Boolean>> ls =
        IntStream.range(0, 20)
            .mapToObj(
                i -> TestUtils.createRandomFile(TestUtils.tmp.resolve(String.valueOf(i)), 1 << 64))
            .map(fileFacade::addFile)
            .map(ob -> ob.toBlocking().first())
            .map(Tuple2::getT2)
            .collect(Collectors.toList());
    //track files
    assertThat(Observable.merge(ls).toBlocking().last()).isTrue();
    //add 2nd provider
    CountDownLatch cd = new CountDownLatch(2);
    Subscription sub =
        appEvents.subscribe(
            event -> {
              if (event instanceof SynchDoneEvent) {
                cd.countDown();
              }
            });
    final FileProvider provider2 = TestUtils.fileProvider(tmp3);
    addProvider(provider2);
    assertThat(cd.await(5, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();

    assertThat(localConfig.getLocalFiles()).hasSize(20);

    Optional<ProviderHook> opt =
        providerLifeCycle
            .currentlyActiveProviders()
            .stream()
            .filter(providerHook -> providerHook.getProvider().equals(provider2))
            .findFirst();

    assertThat(opt.isPresent()).isTrue();
    AcquirerReleaser acquirer = Observable.create(opt.get().getAcquirer()).toBlocking().first();
    assertThat(acquirer).isNotNull();
    assertThat(acquirer.getRemoteConfig().getRemoteFiles()).hasSize(20);
    assertThat(Files.list(tmp3)).hasSize(24);
  }
}
