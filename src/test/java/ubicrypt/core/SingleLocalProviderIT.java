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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.Subscription;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.ProviderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.provider.FileEvent.Type.created;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {UbiConf.class, TestPathConf.class},
  initializers = {TestKeyInitializer.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SingleLocalProviderIT {
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
  public void addProvider() throws Exception {
    assertThat(providerCommander).isNotNull();
    CountDownLatch providerActivated = new CountDownLatch(1);
    Subscription providerActivatedSub =
        providerEvent.subscribe(
            next -> {
              if (next.getEvent() == ProviderStatus.active) {
                providerActivated.countDown();
              }
            });
    assertThat(
            providerCommander.register(TestUtils.fileProvider(TestUtils.tmp2)).toBlocking().last())
        .isTrue();
    //activated
    assertThat(providerActivated.await(1, TimeUnit.SECONDS)).isTrue();
    providerActivatedSub.unsubscribe();
    assertThat(providerLifeCycle.currentlyActiveProviders()).hasSize(1);
  }

  @Test
  public void removeProvider() throws Exception {
    addProvider();
    CountDownLatch providerRemoved = new CountDownLatch(1);
    Subscription providerRemovedSub =
        providerEvent.subscribe(
            next -> {
              if (next.getEvent() == ProviderStatus.removed) {
                providerRemoved.countDown();
              }
            });
    assertThat(
            providerCommander
                .remove(providerLifeCycle.currentlyActiveProviders().get(0).getProvider())
                .toBlocking()
                .last())
        .isTrue();
    //removed
    assertThat(providerRemoved.await(1, TimeUnit.SECONDS)).isTrue();
    providerRemovedSub.unsubscribe();
    assertThat(providerLifeCycle.currentlyActiveProviders()).hasSize(0);
  }

  @Test
  public void addProviderAfterFiles() throws Exception {
    CountDownLatch cd = new CountDownLatch(1);
    Observable.range(0, 10)
        .map(i -> basePath.resolve(String.valueOf(i)))
        .flatMap(
            path -> {
              TestUtils.createRandomFile(path, 1 << 16);
              return fileFacade.addFile(path);
            })
        .flatMap(tupla -> tupla.getT2())
        .subscribe(
            result -> assertThat(result).isFalse(),
            err -> err.printStackTrace(),
            () -> cd.countDown());
    assertThat(cd.await(5, TimeUnit.SECONDS)).isTrue();
    CountDownLatch fileCounter = new CountDownLatch(10);
    addProvider();
    Subscription sub = fileEvents.subscribe(fileEvent -> fileCounter.countDown());
    assertThat(fileCounter.await(5, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();
  }

  @Test
  public void addFiles() throws Exception {
    addProvider();
    CountDownLatch fileCounter = new CountDownLatch(10);
    List<Integer> nums =
        new CopyOnWriteArrayList<>(IntStream.range(0, 10).boxed().collect(Collectors.toList()));
    Subscription sub =
        fileEvents.subscribe(
            fileEvent -> {
              final String fileName = fileEvent.getFile().getPath().toString();
              //check names
              assertThat(nums.remove(Integer.valueOf(fileName.substring(fileName.length() - 1))))
                  .isTrue();
              assertThat(fileEvent.getType()).isEqualTo(created);
              fileCounter.countDown();
            });

    Observable.range(0, 10)
        .map(i -> basePath.resolve(String.valueOf(i)))
        .flatMap(
            path -> {
              TestUtils.createRandomFile(path, 1 << 16);
              return fileFacade.addFile(path);
            })
        .flatMap(tupla -> tupla.getT2())
        .subscribe(
            result -> {
              assertThat(result).isTrue();
            });
    assertThat(fileCounter.await(5, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();
    assertThat(Files.list(TestUtils.tmp2).count()).isEqualTo(12);
    //all files are processed
    assertThat(nums).isEmpty();
  }

  @Test
  public void untrackFiles() throws Exception {
    addFiles();
    CountDownLatch fileCounter = new CountDownLatch(10);
    Observable.range(0, 10)
        .map(i -> basePath.resolve(String.valueOf(i)))
        .flatMap(path -> fileFacade.removeFile(path))
        .subscribe(
            result -> {
              assertThat(result).isTrue();
              fileCounter.countDown();
            });

    assertThat(fileCounter.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(Files.list(TestUtils.tmp2).count()).isEqualTo(2);
    assertThat(localConfig.getLocalFiles().stream().filter(LocalFile::isRemoved)).hasSize(10);
    ProviderHook hook = providerLifeCycle.currentlyActiveProviders().get(0);
    assertThat(
            Observable.create(hook.getAcquirer())
                .toBlocking()
                .first()
                .getRemoteConfig()
                .getRemoteFiles()
                .stream()
                .filter(RemoteFile::isRemoved))
        .hasSize(10);
  }
}
