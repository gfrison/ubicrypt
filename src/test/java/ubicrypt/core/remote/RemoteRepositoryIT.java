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
package ubicrypt.core.remote;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.PublishSubject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.provider.lock.ObjectIO;
import ubicrypt.core.util.Persist;
import ubicrypt.core.util.QueueLiner;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.functions.Actions.empty;
import static ubicrypt.core.TestUtils.fileProvider;

public class RemoteRepositoryIT {

  private static final Logger log = getLogger(RemoteRepositoryIT.class);
  private static final int deviceId = Utils.deviceId();

  @Before
  public void setUp() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.createDirs();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteR(TestUtils.tmp);
    TestUtils.deleteR(TestUtils.tmp2);
  }

  @Test
  public void error() throws Exception {
    RemoteFile rf = new RemoteFile();
    final RemoteConfig remoteConfig = new RemoteConfig();
    remoteConfig.getRemoteFiles().add(rf);
    CountDownLatch cd = new CountDownLatch(1);
    Observable.OnSubscribe<AcquirerReleaser> acquirer =
        subscriber -> {
          subscriber.onNext(new AcquirerReleaser(remoteConfig, empty()));
          subscriber.onCompleted();
        };
    RemoteIO<RemoteConfig> configIO =
        new RemoteIO<RemoteConfig>() {
          @Override
          public Observable<Boolean> apply(RemoteConfig remoteConfig) {
            assertThat(remoteConfig.getRemoteFiles().iterator().next().isError()).isTrue();
            cd.countDown();
            return Observable.just(true);
          }

          @Override
          public void call(Subscriber<? super RemoteConfig> subscriber) {}
        };
    RemoteRepository rr = new RemoteRepository(acquirer, fileProvider(TestUtils.tmp), configIO);
    rr.error(rf);
    assertThat(cd.await(2, SECONDS)).isTrue();
  }

  @Test
  public void save() throws Exception {
    final PGPService pgp = new PGPService(PGPEC.encryptionKey(), new LocalConfig());

    final RemoteConfig remoteConfig = new RemoteConfig();
    Observable.OnSubscribe<AcquirerReleaser> acquirer =
        subscriber -> {
          subscriber.onNext(new AcquirerReleaser(remoteConfig, empty()));
          subscriber.onCompleted();
        };
    final FileProvider provider = fileProvider(TestUtils.tmp);
    final Persist ser =
        new Persist(provider) {
          {
            setPgpService(pgp);
          }
        };
    provider
        .getConfFile()
        .setKey(
            new Key() {
              {
                setType(UbiFile.KeyType.pgp);
              }
            });
    ser.putObject(remoteConfig, provider.getConfFile()).toBlocking().last();

    final LocalRepository localRepository = new LocalRepository(TestUtils.tmp2);
    Utils.write(TestUtils.tmp2.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    final PublishSubject<ProgressFile> progress = PublishSubject.create();
    final PublishSubject<FileEvent> file2Events = PublishSubject.create();

    final RemoteRepository repo =
        new RemoteRepository(
            acquirer, provider, new ObjectIO<>(ser, provider.getConfFile(), RemoteConfig.class)) {
          {
            setProgressEvents(progress);
            setFileEvents(file2Events);
            setQueueLiner(new QueueLiner(1000));
          }
        };
    repo.setActions(
        Arrays.asList(
            new OnUpdateRemote(provider, repo) {
              {
                setProgressEvents(progress);
                setFileEvents(file2Events);
              }
            },
            new OnInsertRemote(provider, repo) {
              {
                setProgressEvents(progress);
                setFileEvents(file2Events);
              }
            }));
    repo.init();

    final LocalFile localFile =
        new LocalFile() {
          {
            setPath(Paths.get("origin"));
          }
        };
    localRepository.getLocalConfig().getLocalFiles().add(localFile);
    final ArrayList<ProgressFile> progresses = new ArrayList<>();
    progress.subscribe(progresses::add);

    //create
    CountDownLatch cd1 = new CountDownLatch(1);
    Subscription sub =
        file2Events.subscribe(
            fileEvent -> {
              assertThat(fileEvent.getType()).isEqualTo(FileEvent.Type.created);
              assertThat(fileEvent.getFile()).isEqualTo(localFile);
              cd1.countDown();
            });
    assertThat(repo.save(new FileProvenience(localFile, localRepository)).toBlocking().last())
        .isTrue();
    assertThat(remoteConfig.getRemoteFiles()).hasSize(1);
    assertThat(
            IOUtils.readLines(
                repo.get(remoteConfig.getRemoteFiles().iterator().next()).toBlocking().first()))
        .contains("ciao");
    assertThat(progresses).hasSize(4); //2 put, 2 get
    Iterator<ProgressFile> it = progresses.iterator();
    ProgressFile next = it.next();
    assertThat(next.isCompleted()).isFalse();
    assertThat(next.getChunk()).isGreaterThan(0);
    assertThat(it.next().isCompleted()).isTrue();
    progresses.clear();
    assertThat(cd1.await(2, SECONDS)).isTrue();
    sub.unsubscribe();

    CountDownLatch cd2 = new CountDownLatch(1);
    sub =
        file2Events.subscribe(
            fileEvent -> {
              assertThat(fileEvent.getType()).isEqualTo(FileEvent.Type.updated);
              assertThat(fileEvent.getFile()).isEqualTo(localFile);
              cd2.countDown();
            });
    repo.setFileEvents(file2Events);
    Utils.write(TestUtils.tmp2.resolve("origin"), "ciao2".getBytes()).toBlocking().last();
    localFile.getVclock().increment(deviceId);
    assertThat(repo.save(new FileProvenience(localFile, localRepository)).toBlocking().last())
        .isTrue();
    assertThat(localFile.compare(remoteConfig.getRemoteFiles().iterator().next()))
        .isEqualTo(VClock.Comparison.equal);
    assertThat(
            IOUtils.readLines(
                repo.get(remoteConfig.getRemoteFiles().iterator().next()).toBlocking().first()))
        .contains("ciao2");
    it = progresses.iterator();
    next = it.next();
    assertThat(next.isCompleted()).isFalse();
    assertThat(next.getChunk()).isGreaterThan(0);
    assertThat(it.next().isCompleted()).isTrue();
    progresses.clear();
    assertThat(cd2.await(2, SECONDS)).isTrue();
    sub.unsubscribe();

    //not update
    repo.setFileEvents(file2Events);
    final VClock vClock = (VClock) localFile.getVclock().clone();
    assertThat(repo.save(new FileProvenience(localFile, localRepository)).toBlocking().last())
        .isFalse();
    assertThat(remoteConfig.getRemoteFiles().iterator().next().getVclock().compare(vClock))
        .isEqualTo(VClock.Comparison.equal);
    assertThat(progresses).isEmpty();
    final CountDownLatch cd = new CountDownLatch(1);
    file2Events.subscribe(event -> cd.countDown());
    if (cd.await(1, SECONDS)) {
      Assertions.fail("update event not expected");
    }

    //delete
    CountDownLatch cd3 = new CountDownLatch(1);
    sub =
        file2Events.subscribe(
            fileEvent -> {
              assertThat(fileEvent.getType()).isEqualTo(FileEvent.Type.deleted);
              assertThat(fileEvent.getFile()).isEqualTo(localFile);
              cd3.countDown();
            });
    repo.setFileEvents(file2Events);
    localFile.setDeleted(true);
    localFile.getVclock().increment(deviceId);
    assertThat(repo.save(new FileProvenience(localFile, localRepository)).toBlocking().last())
        .isTrue();
    assertThat(remoteConfig.getRemoteFiles().iterator().next().isDeleted()).isTrue();
    try {
      assertThat(
              IOUtils.readLines(
                  repo.get(remoteConfig.getRemoteFiles().iterator().next()).toBlocking().first()))
          .contains("ciao2");
      Assertions.fail("file still exists");
    } catch (final NotFoundException e) {

    }
    assertThat(cd3.await(2, SECONDS)).isTrue();
    sub.unsubscribe();
  }
}
