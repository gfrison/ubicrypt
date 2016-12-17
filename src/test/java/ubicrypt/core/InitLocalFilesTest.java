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

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;

import ubicrypt.core.dto.LocalFile;

import static org.assertj.core.api.Assertions.assertThat;

public class InitLocalFilesTest {
  @Test
  public void testName() throws Exception {
    final Path target = Paths.get(UUID.randomUUID().toString());

    final Path file = Files.createFile(target);
    final LocalFile ufile = new LocalFile();
    ufile.setPath(file);
    ufile.setLastModified(Instant.MIN);
    final InitLocalFiles fi = new InitLocalFiles();
    fi.basePath = Paths.get(System.getProperty("java.io.tmpdir"));
    fi.accept(ufile);
    assertThat(
        ufile.getLastModified().isAfter(Instant.ofEpochMilli(System.currentTimeMillis() - 1000)));
    assertThat(ufile.getSize())
        .isEqualTo(Files.readAttributes(file, BasicFileAttributes.class).size());
    Files.delete(file);
  }
}
