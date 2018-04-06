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
package ubicrypt.core.local;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterInputStream;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Actions;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.JustOnSubscribe;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.provider.lock.ObjectIO;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.remote.RemoteRepository;
import ubicrypt.core.util.Persist;
import ubicrypt.core.util.QueueLiner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static ubicrypt.core.TestUtils.tmp;
import static ubicrypt.core.TestUtils.tmp2;
import static ubicrypt.core.provider.FileEvent.Type.created;
import static ubicrypt.core.provider.FileEvent.Type.updated;

public class LocalRepositoryTest {
  private static final int deviceId = Utils.deviceId();

  @Before
  public void setUp() throws Exception {
    TestUtils.deleteR(tmp);
    TestUtils.deleteR(tmp2);
    Files.createDirectories(tmp);
    Files.createDirectories(tmp2);
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteR(tmp);
    TestUtils.deleteR(tmp2);
  }

  @Test
  public void inflaterException() throws Exception {
    final LocalRepository lr = new LocalRepository(tmp);
    lr.setOnNewFileLocal(
        new OnNewLocal() {
          {
            setBasePath(tmp);
            setLocalConfig(new LocalConfig());
            setFileEvents(PublishSubject.create());
          }
        });
    final LocalFile file = new LocalFile();
    Files.createFile(tmp2.resolve("fileName"));
    Utils.write(
        tmp2.resolve("fileName"),
        "ciaoooooooooooooooooooooooo000000000000000000000000000000000000000000000".getBytes());
    file.setPath(Paths.get("fileName"));
    final FileProvider provider = TestUtils.fileProvider(tmp2);
    RemoteConfig rconfig = new RemoteConfig();
    rconfig.getProviders().add(provider);
    final RemoteFile remoteFile = RemoteFile.createFrom(file);
    remoteFile.setRemoteName("fileName");
    rconfig.getRemoteFiles().add(remoteFile);
    RemoteRepository repo =
        new RemoteRepository(
            new JustOnSubscribe<>(new AcquirerReleaser(rconfig, () -> {})),
            provider,
            new RemoteIO<RemoteConfig>() {
              @Override
              public Observable<Boolean> apply(RemoteConfig remoteConfig) {
                return Observable.just(true);
              }

              @Override
              public void call(Subscriber<? super RemoteConfig> subscriber) {
                subscriber.onNext(rconfig);
                subscriber.onCompleted();
              }
            });
    repo.setQueueLiner(new QueueLiner(100));
    repo.setFileEvents(PublishSubject.create());
    repo.init();
    assertThat(lr.save(new FileProvenience(file, repo)).toBlocking().last()).isFalse();
  }

  @Test
  public void save() throws Exception {
    final LocalRepository lr = new LocalRepository(tmp);
    final Subject<FileEvent, FileEvent> fevents = PublishSubject.create();
    lr.setFileEvents(fevents);
    lr.setOnNewFileLocal(
        new OnNewLocal() {
          {
            setFileEvents(fevents);
            setBasePath(tmp);
            setLocalConfig(lr.getLocalConfig());
          }
        });

    final byte[] key = AESGCM.rndKey();
    final FileProvider provider = TestUtils.fileProvider(tmp2);
    Utils.write(
            tmp2.resolve("origin"),
            AESGCM.encryptIs(
                key, new DeflaterInputStream(new ByteArrayInputStream("ciao".getBytes()))))
        .toBlocking()
        .last();
    final RemoteFile rf =
        new RemoteFile() {
          {
            setRemoteName("origin");
            setPath(Paths.get("local"));
            setKey(new Key(key));
            setSize(4);
          }
        };

    final RemoteConfig remoteConfig = new RemoteConfig();
    remoteConfig.getRemoteFiles().add(rf);
    Observable.OnSubscribe<AcquirerReleaser> acquirer =
        (subscriber) -> {
          subscriber.onNext(new AcquirerReleaser(remoteConfig, Actions.empty()));
          subscriber.onCompleted();
        };
    final Persist ser =
        new Persist(provider) {
          {
            setPgpService(mock(IPGPService.class));
          }
        };
    ser.putObject(new RemoteConfig(), provider.getConfFile()).toBlocking().first();

    final RemoteRepository repo =
        new RemoteRepository(
            acquirer, provider, new ObjectIO<>(ser, provider.getConfFile(), RemoteConfig.class)) {
          {
            //            setSerializer(ser);
          }
        };
    repo.setQueueLiner(new QueueLiner(100));
    repo.init();
    assertThat(IOUtils.readLines(repo.get(rf).toBlocking().first())).contains("ciao");

    //create
    final CountDownLatch cd3 = new CountDownLatch(1);
    Subscription sub =
        fevents.subscribe(
            event -> {
              assertThat(event.getType()).isEqualTo(created);
              cd3.countDown();
            });
    assertThat(lr.save(new FileProvenience(rf, repo)).toBlocking().last()).isTrue();
    assertThat(IOUtils.readLines(lr.get(rf).toBlocking().last())).contains("ciao");
    assertThat(lr.getLocalConfig().getLocalFiles()).hasSize(1);
    assertThat(cd3.await(2, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();

    //update
    rf.getVclock().increment(deviceId);
    rf.setSize(5);
    final CountDownLatch cd4 = new CountDownLatch(1);
    sub =
        fevents.subscribe(
            event -> {
              assertThat(event.getType()).isEqualTo(updated);
              cd4.countDown();
            });
    Utils.write(
            tmp2.resolve("origin"),
            AESGCM.encryptIs(
                key, new DeflaterInputStream(new ByteArrayInputStream("ciao2".getBytes()))))
        .toBlocking()
        .last();
    assertThat(lr.save(new FileProvenience(rf, repo)).toBlocking().last()).isTrue();
    assertThat(IOUtils.readLines(lr.get(rf).toBlocking().last())).contains("ciao2");
    assertThat(lr.getLocalConfig().getLocalFiles()).hasSize(1);
    assertThat(lr.getLocalConfig().getLocalFiles().iterator().next().compare(rf))
        .isEqualTo(VClock.Comparison.equal);
    assertThat(cd3.await(2, TimeUnit.SECONDS)).isTrue();
    sub.unsubscribe();

    //not update
    assertThat(lr.save(new FileProvenience(rf, repo)).toBlocking().first()).isFalse();
    assertThat(IOUtils.readLines(lr.get(rf).toBlocking().first())).contains("ciao2");
    assertThat(lr.getLocalConfig().getLocalFiles()).hasSize(1);
    assertThat(lr.getLocalConfig().getLocalFiles().iterator().next().compare(rf))
        .isEqualTo(VClock.Comparison.equal);
  }
}
