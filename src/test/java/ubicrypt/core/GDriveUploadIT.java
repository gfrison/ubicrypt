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
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.fn.tuple.Tuple2;
import rx.Observable;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.gdrive.GDriveConf;
import ubicrypt.core.provider.gdrive.GDriveProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {UbiConf.class, TestPathConf.class},
  initializers = {TestKeyInitializer.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@ConditionalOnProperty(name = "user.name", value = "gfrison")
public class GDriveUploadIT {
  private static final Logger log = getLogger(GDriveUploadIT.class);
  private static GDriveConf conf;
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
  public void init() throws GeneralSecurityException, IOException {
    conf = TestUtils.createGDriveConf();
    TestUtils.deleteDirs();
    TestUtils.createDirs();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
  }

  @Test
  public void trackUntrack() throws Exception {
    int numFiles = 10;
    final int size = 1 << 16;
    GDriveProvider provider = new GDriveProvider(conf);
    assertThat(providerCommander.register(provider).toBlocking().last()).isTrue();

    //create 20 files
    List<Observable<Boolean>> ls =
        IntStream.range(0, numFiles)
            .mapToObj(
                i -> TestUtils.createRandomFile(TestUtils.tmp.resolve(String.valueOf(i)), size))
            .map(fileFacade::addFile)
            .map(ob -> ob.toBlocking().first())
            .map(Tuple2::getT2)
            .collect(Collectors.toList());
    //track files
    assertThat(Observable.merge(ls).toBlocking().last()).isTrue();

    Optional<ProviderHook> opt =
        providerLifeCycle
            .currentlyActiveProviders()
            .stream()
            .filter(providerHook -> providerHook.getProvider().equals(provider))
            .findFirst();
    assertThat(opt.isPresent()).isTrue();
    AcquirerReleaser acquirer = Observable.create(opt.get().getAcquirer()).toBlocking().first();
    assertThat(acquirer).isNotNull();
    Set<RemoteFile> remoteFiles = acquirer.getRemoteConfig().getRemoteFiles();
    assertThat(remoteFiles).hasSize(numFiles);

    Thread.sleep(2000);
    assertThat(
            provider
                .getDrive()
                .files()
                .list()
                .setQ(String.format("'%s' in parents", conf.getFolderId()))
                .execute()
                .getFiles()
                .size())
        .isEqualTo(numFiles + 2);

    //untrack
    List<Observable<Boolean>> untracked =
        IntStream.range(0, numFiles)
            .mapToObj(i -> fileFacade.removeFile(TestUtils.tmp.resolve(String.valueOf(i))))
            .collect(Collectors.toList());
    assertThat(Observable.merge(untracked).toBlocking().last()).isTrue();
    opt =
        providerLifeCycle
            .currentlyActiveProviders()
            .stream()
            .filter(providerHook -> providerHook.getProvider().equals(provider))
            .findFirst();
    assertThat(opt.isPresent()).isTrue();
    acquirer = Observable.create(opt.get().getAcquirer()).toBlocking().first();
    assertThat(acquirer).isNotNull();
    remoteFiles = acquirer.getRemoteConfig().getRemoteFiles();
    //check remote files are deleted
    assertThat(remoteFiles.stream().filter(remoteFile -> !remoteFile.isRemoved())).hasSize(0);
    assertThat(
            provider
                .getDrive()
                .files()
                .list()
                .setQ(String.format("'%s' in parents", conf.getFolderId()))
                .execute()
                .getFiles()
                .size())
        .isEqualTo(2);
    provider.getDrive().files().delete(conf.getFolderId()).execute();
  }
}
