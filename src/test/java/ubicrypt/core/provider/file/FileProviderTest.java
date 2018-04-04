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
package ubicrypt.core.provider.file;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import ubicrypt.core.TestUtils;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.util.Persist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class FileProviderTest {
  private FileProvider fp;

  @Before
  public void setUp() throws Exception {
    TestUtils.createDirs();
    fp = TestUtils.fileProvider(TestUtils.tmp);
    final Persist ser =
        new Persist(fp) {
          {
            setPgpService(mock(IPGPService.class));
          }
        };
    ser.putObject(new RemoteConfig(providers, records, maxFilesPerIndex), fp.getConfFile()).toBlocking().first();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
  }

  @Test
  public void small() throws Exception {
    crud(5);
  }

  @Test
  public void chunk() throws Exception {
    crud(1 << 16);
  }

  @Test
  public void big() throws Exception {
    crud(999999);
  }

  private void crud(final int size) throws Exception {
    final byte[] bytes = new byte[size];
    new Random().nextBytes(bytes);

    final AtomicBoolean b = new AtomicBoolean(false);
    final AtomicReference<String> fname = new AtomicReference<>();
    fp.post(new ByteArrayInputStream(bytes))
        .toBlocking()
        .forEach(
            name -> {
              fname.set(name);
              assertThat(b.compareAndSet(false, true)).isTrue();
              assertThat(name).isNotNull();
            });

    assertThat(IOUtils.toByteArray(fp.get(fname.get()).toBlocking().first())).isEqualTo(bytes);

    new Random().nextBytes(bytes);
    fp.put(fname.get(), new ByteArrayInputStream(bytes)).toBlocking().last();

    assertThat(IOUtils.toByteArray(fp.get(fname.get()).toBlocking().first())).isEqualTo(bytes);

    fp.delete(fname.get()).toBlocking().firstOrDefault(null);

    assertThatThrownBy(() -> fp.get(fname.get()).toBlocking().first());
  }
}
