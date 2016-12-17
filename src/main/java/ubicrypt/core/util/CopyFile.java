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

import com.google.common.base.Throwables;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import rx.functions.Func1;

import static org.slf4j.LoggerFactory.getLogger;

public class CopyFile implements Func1<Path, Boolean> {
  private static final Logger log = getLogger(CopyFile.class);
  private final long size;
  private final Path target;
  private final boolean newFile;
  private final Instant lastModified;

  public CopyFile(
    final long size, final Path target, final boolean newFile, final Instant lastModified) {
    this.size = size;
    this.target = target;
    this.newFile = newFile;
    this.lastModified = lastModified;
  }

  @Override
  public Boolean call(final Path path) {
    try {
      final Path dir = Paths.get(target.toFile().getParent());
      if (!Files.exists(dir)) {
        log.info("create directories for:{}", dir);
        Files.createDirectories(dir);
      }
      final long nbytes =
        FileChannel.open(path)
          .transferTo(
            0,
            size,
            FileChannel.open(
              target,
              (newFile) ? StandardOpenOption.CREATE_NEW : StandardOpenOption.CREATE,
              StandardOpenOption.WRITE));
      log.debug("copied from:{} to:{} bytes:{}", path, target, nbytes);
      if (nbytes != size) {
        throw new IllegalStateException(
          String.format("copied %s bytes instead of %s", nbytes, size));
      }
      Files.setLastModifiedTime(target, FileTime.from(lastModified));
      return true;
    } catch (final IOException e) {
      Throwables.propagate(e);
    }
    return null;
  }
}
