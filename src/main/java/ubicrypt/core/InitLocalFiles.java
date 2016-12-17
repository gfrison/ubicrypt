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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;

/**
 * increment vclock for each local file has been modified before startup.
 */
public class InitLocalFiles implements Consumer<LocalFile> {
  private final Logger log = LoggerFactory.getLogger(InitLocalFiles.class);
  @Inject
  LocalConfig localConfig;
  @Inject
  Path basePath;
  @Inject
  int deviceId;

  @PostConstruct
  @Async
  public void init() {
    localConfig.getLocalFiles().stream().forEach(this);
  }

  @Override
  public void accept(final LocalFile localFile) {
    boolean modified = false;
    try {
      final BasicFileAttributes attr =
        Files.readAttributes(basePath.resolve(localFile.getPath()), BasicFileAttributes.class);
      if (attr.lastModifiedTime().toInstant().isAfter(localFile.getLastModified())) {
        log.debug(
          "file:{}, different modified time. Config:{}, actual:{}",
          localFile.getPath(),
          localFile.getLastModified(),
          attr.lastModifiedTime());
        localFile.setLastModified(attr.lastModifiedTime().toInstant());
        if (attr.size() != localFile.getSize()) {
          log.debug(
            "file:{}, different size. Config:{}, actual:{}",
            localFile.getPath(),
            localFile.getSize(),
            attr.size());
          localFile.setSize(attr.size());
          modified = true;
        }
      }
    } catch (final NoSuchFileException e) {
      log.info("file:{} has been deleted", localFile.getPath());
      if (!localFile.isDeleted()) {
        localFile.setDeleted(true);
        modified = true;
      }
    } catch (final IOException e) {
      log.error(e.getMessage(), e);
    }
    if (modified) {
      localFile.getVclock().increment(deviceId);
    }
  }
}
