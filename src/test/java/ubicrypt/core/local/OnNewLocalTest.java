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

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.FileEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static ubicrypt.core.TestUtils.createDirs;
import static ubicrypt.core.TestUtils.deleteDirs;
import static ubicrypt.core.TestUtils.tmp;

public class OnNewLocalTest {

    private OnNewLocal test;
    private Subject<FileEvent, FileEvent> fileEvents = BufferUntilSubscriber.create();
    private LocalConfig localConfig = new LocalConfig();

    @Before
    public void setUp() throws Exception {
        deleteDirs();
        createDirs();
        test = new OnNewLocal();
        test.setBasePath(tmp);
        test.setFileEvents(fileEvents);
        test.setLocalConfig(localConfig);
    }

    @After
    public void tearDown() throws Exception {
        deleteDirs();
    }

    @Test
    public void ok() throws Exception {
        final Path pathf = Paths.get("ciao");
        AtomicReference<FileEvent> event = new AtomicReference<>();
        CountDownLatch cd = new CountDownLatch(1);
        CountDownLatch cd2 = new CountDownLatch(1);
        fileEvents.subscribe(next -> {
            event.set(next);
            cd.countDown();
        }, Utils.logError);
        final ByteArrayInputStream inputStream = new ByteArrayInputStream("ciao".getBytes());
        final RemoteFile remoteFile = new RemoteFile() {{
            setRemoteName("a");
            setPath(pathf);
            setSize(4);
        }};
        Observable<Boolean> res = test.call(new FileProvenience(remoteFile, new IRepository() {
            @Override
            public Observable<InputStream> get(UbiFile file) {
                assertThat(file.getPath().equals(pathf));
                return Observable.just(inputStream);
            }
        }));
        res.subscribe(res2 -> {
            assertThat(res2).isTrue();
            cd2.countDown();
        }, err -> fail(err.getMessage(), err));
        assertThat(cd2.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(cd.await(2, TimeUnit.SECONDS)).isTrue();
        final FileEvent fileEvent = event.get();
        assertThat(fileEvent.getFile()).isEqualTo(remoteFile);
        assertThat(fileEvent.getLocation()).isEqualTo(FileEvent.Location.local);
        assertThat(fileEvent.getType()).isEqualTo(FileEvent.Type.created);
        assertThat(localConfig.getLocalFiles()).hasSize(1);
        assertThat(localConfig.getLocalFiles().iterator().next().getPath()).isEqualTo(remoteFile.getPath());
        assertThat(FileUtils.readFileToString(tmp.resolve(pathf).toFile(), "UTF-8")).isEqualTo("ciao");
    }

    @Test
    public void notFound() throws Exception {
        final Path pathf = Paths.get("ciao");
        AtomicReference<FileEvent> event = new AtomicReference<>();
        CountDownLatch cd = new CountDownLatch(1);
        CountDownLatch cd2 = new CountDownLatch(1);
        CountDownLatch cd3 = new CountDownLatch(1);
        fileEvents.subscribe(next -> {
            event.set(next);
            cd.countDown();
        }, Utils.logError);
        final RemoteFile remoteFile = new RemoteFile() {{
            setRemoteName("a");
            setPath(pathf);
            setSize(4);
        }};
        Observable<Boolean> res = test.call(new FileProvenience(remoteFile, new IRepository() {
            @Override
            public Observable<InputStream> get(UbiFile file) {
                return Observable.error(new NotFoundException(file));
            }

            @Override
            public void error(UbiFile file) {
                assertThat(file).isEqualTo(remoteFile);
                cd3.countDown();
            }
        }));
        res.subscribe(res2 -> {
            assertThat(res2).isFalse();
            cd2.countDown();
        }, err -> fail(err.getMessage(), err));
        assertThat(cd2.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(cd.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(cd3.await(2, TimeUnit.SECONDS)).isTrue();
        final FileEvent fileEvent = event.get();
        assertThat(fileEvent.getFile()).isEqualTo(remoteFile);
        assertThat(fileEvent.getLocation()).isEqualTo(FileEvent.Location.local);
        assertThat(fileEvent.getType()).isEqualTo(FileEvent.Type.error);
        assertThat(localConfig.getLocalFiles()).hasSize(0);
    }
}
