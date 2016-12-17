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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import rx.Observable;
import ubicrypt.core.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class StoreTempFileTest {
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
    final Path path =
        Observable.just(new ByteArrayInputStream("ciao".getBytes()))
            .flatMap(new StoreTempFile())
            .toBlocking()
            .first();
    assertThat(path).isNotNull();
    assertThat(Files.readAllBytes(path)).isEqualTo("ciao".getBytes());
  }
}
