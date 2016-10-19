/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
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
import rx.Subscription;
import rx.functions.Actions;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.remote.RemoteRepository;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.provider.lock.ObjectIO;
import ubicrypt.core.util.ObjectSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static ubicrypt.core.TestUtils.tmp;
import static ubicrypt.core.provider.FileEvent.Type.created;
import static ubicrypt.core.provider.FileEvent.Type.updated;

public class LocalRepositoryTest {
    private final static int deviceId = Utils.deviceId();

    @Before
    public void setUp() throws Exception {
        TestUtils.deleteR(tmp);
        TestUtils.deleteR(TestUtils.tmp2);
        Files.createDirectories(tmp);
        Files.createDirectories(TestUtils.tmp2);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteR(tmp);
        TestUtils.deleteR(TestUtils.tmp2);

    }

    @Test
    public void save() throws Exception {
        final LocalRepository lr = new LocalRepository(tmp);
        final Subject<FileEvent, FileEvent> fevents = PublishSubject.create();
        lr.setFileEvents(fevents);
        lr.setOnNewFileLocal(new OnNewLocal() {{
            setFileEvents(fevents);
            setBasePath(tmp);
            setLocalConfig(lr.getLocalConfig());
        }});

        final byte[] key = AESGCM.rndKey();
        final FileProvider provider = TestUtils.fileProvider(TestUtils.tmp2);
        Utils.write(TestUtils.tmp2.resolve("origin"), AESGCM.encryptIs(key, new DeflaterInputStream(new ByteArrayInputStream("ciao".getBytes())))).toBlocking().last();
        final RemoteFile rf = new RemoteFile() {{
            setRemoteName("origin");
            setPath(Paths.get("local"));
            setKey(new Key(key));
            setSize(4);
        }};

        final RemoteConfig remoteConfig = new RemoteConfig();
        remoteConfig.getRemoteFiles().add(rf);
        Observable.OnSubscribe<AcquirerReleaser> acquirer = (subscriber) -> {
            subscriber.onNext(new AcquirerReleaser(remoteConfig, Actions.empty()));
            subscriber.onCompleted();
        };
        final ObjectSerializer ser = new ObjectSerializer(provider) {{
            setPgpService(mock(IPGPService.class));
        }};
        ser.putObject(new RemoteConfig(), provider.getConfFile()).toBlocking().first();

        final RemoteRepository repo = new RemoteRepository(acquirer, provider, new ObjectIO<>(ser, provider.getConfFile(), RemoteConfig.class)) {{
//            setSerializer(ser);
        }};

        assertThat(IOUtils.readLines(repo.get(rf).toBlocking().first())).contains("ciao");

        //create
        final CountDownLatch cd3 = new CountDownLatch(1);
        Subscription sub = fevents.subscribe(event -> {
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
        sub = fevents.subscribe(event -> {
            assertThat(event.getType()).isEqualTo(updated);
            cd4.countDown();
        });
        Utils.write(TestUtils.tmp2.resolve("origin"), AESGCM.encryptIs(key, new DeflaterInputStream(new ByteArrayInputStream("ciao2".getBytes())))).toBlocking().last();
        assertThat(lr.save(new FileProvenience(rf, repo)).toBlocking().last()).isTrue();
        assertThat(IOUtils.readLines(lr.get(rf).toBlocking().last())).contains("ciao2");
        assertThat(lr.getLocalConfig().getLocalFiles()).hasSize(1);
        assertThat(lr.getLocalConfig().getLocalFiles().iterator().next().compare(rf)).isEqualTo(VClock.Comparison.equal);
        assertThat(cd3.await(2, TimeUnit.SECONDS)).isTrue();
        sub.unsubscribe();

        //not update
        assertThat(lr.save(new FileProvenience(rf, repo)).toBlocking().first()).isFalse();
        assertThat(IOUtils.readLines(lr.get(rf).toBlocking().first())).contains("ciao2");
        assertThat(lr.getLocalConfig().getLocalFiles()).hasSize(1);
        assertThat(lr.getLocalConfig().getLocalFiles().iterator().next().compare(rf)).isEqualTo(VClock.Comparison.equal);

    }
}
