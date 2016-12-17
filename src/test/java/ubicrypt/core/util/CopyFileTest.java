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
package ubicrypt.core.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import rx.Observable;
import ubicrypt.core.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class CopyFileTest {
  @Before
  public void setUp() throws Exception {
    TestUtils.createDirs();
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
  }

  @Test
  public void testName() throws Exception {
    final Path tempFile = Files.createTempFile(null, null);
    Files.write(tempFile, "ciao".getBytes());
    final Path target = TestUtils.tmp.resolve(UUID.randomUUID().toString());
    assertThat(
            Observable.just(tempFile)
                .map(new CopyFile(4, target, true, Instant.now()))
                .toBlocking()
                .first())
        .isTrue();
    assertThat(Files.readAllBytes(target)).isEqualTo("ciao".getBytes());
    assertThat(
            Observable.just(tempFile)
                .map(new CopyFile(4, target, false, Instant.now()))
                .toBlocking()
                .first())
        .isTrue();
    assertThat(Files.readAllBytes(target)).isEqualTo("ciao".getBytes());
  }
}
