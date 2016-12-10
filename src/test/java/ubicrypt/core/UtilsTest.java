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
package ubicrypt.core;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.Utils.ubiqFolder;

public class UtilsTest {
    @Before
    public void setUp() throws Exception {
        TestUtils.createDirs();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteDirs();
    }

    @Test
    public void testUbiqConfolder() throws Exception {
        assertThat(ubiqFolder()).isNotNull();
    }

    @Test
    public void testLock() throws Exception {
        assertThat(Utils.isAppInUse(ubiqFolder())).isFalse();
        assertThat(Utils.isAppInUse(ubiqFolder())).isTrue();
    }

    @Test
    public void writebig() throws Exception {
        write(999999);
    }

    @Test
    public void writeSmall() throws Exception {
        write(4);
    }

    @Test
    public void write1chunk() throws Exception {
        write(1 << 16);
    }

    private void write(final int size) throws InterruptedException, IOException {
        final Path target = Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        final byte[] bytes = new byte[size];
        new Random().nextBytes(bytes);

        final AtomicLong length = new AtomicLong(0);
        final CountDownLatch cd = new CountDownLatch(1);
        Utils.write(target, new ByteArrayInputStream(bytes)).subscribe(sizef -> {
            length.set(sizef);
        }, Throwable::printStackTrace, () -> {
            cd.countDown();
        });
        if (!cd.await(2, TimeUnit.SECONDS)) {
            Assertions.fail("not arrived completed");
        }
        assertThat(length.get()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(target)).isEqualTo(bytes);
        Files.delete(target);
    }

    @Test
    public void readIs() throws Exception {
        final SecureRandom rnd = new SecureRandom();
        rnd.setSeed(System.currentTimeMillis());
        final byte[] key = new byte[3 * (1 << 16)];
        rnd.nextBytes(key);

        final Path path = Files.createTempFile(TestUtils.tmp, "a", "b");
        Utils.write(path, new ByteArrayInputStream(key)).toBlocking().last();

        final byte[] bytes = IOUtils.toByteArray(Utils.readIs(path));
        final byte[] bytes2 = IOUtils.toByteArray(Files.newInputStream(path));
        assertThat(bytes.length).isEqualTo(bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            assertThat(bytes[i]).isEqualTo(bytes2[i]);
        }
    }

    @Test
    public void instantSerialize() throws Exception {
        final String now = IOUtils.toString(Utils.marshall(Instant.now()));
        assertThat(now).contains("T", "Z");
    }

    @Test
    public void systemProperties() throws Exception {
        Utils.setProperties(new String[]{"--conf", "ciao", "-a", "=", "b", "--vero"});
        Utils.setProperties(new String[]{"--conf", "ciao", "-a", "=", "b", "--vero", "--conf2=1", "-c", "8"});
        assertThat(System.getProperty("conf")).isEqualTo("ciao");
        assertThat(System.getProperty("a")).isEqualTo("b");
        assertThat(System.getProperty("vero")).isEqualTo("true");
        assertThat(System.getProperty("conf2")).isEqualTo("1");
        assertThat(System.getProperty("c")).isEqualTo("8");
    }
}
