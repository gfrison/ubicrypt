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

import java.nio.file.Path;
import java.util.Optional;

import javax.inject.Inject;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.watch.WatcherBroadcaster;

import static org.slf4j.LoggerFactory.getLogger;

public class FileFacade implements IFileCommander {

  private static final Logger log = getLogger(FileFacade.class);

  @Inject
  FileCommander fileCommander;
  @Inject
  WatcherBroadcaster watcher;
  @Inject
  Path basePath;
  @Inject
  LocalConfig localConfig;

  @Override
  public Observable<Tuple2<LocalFile, Observable<Boolean>>> addFile(final Path absolutePath) {
    final Path relPath = basePath.relativize(absolutePath);
    final Optional<LocalFile> optFile =
      localConfig
        .getLocalFiles()
        .stream()
        .filter(lfile -> lfile.getPath().equals(relPath))
        .findFirst();
    if (optFile.isPresent()) {
      log.debug("file already present:{} ", optFile.get());
      final LocalFile localFile = optFile.get();
      if (localFile.isRemoved()) {
        return Observable.just(
          Tuple.of(
            optFile.get(),
            fileCommander.update(absolutePath, (file) -> file.setRemoved(false))));
      }
      if (localFile.isDeleted()) {
        return Observable.just(
          Tuple.of(
            optFile.get(),
            fileCommander.update(absolutePath, (file) -> file.setDeleted(false))));
      }
    }
    return fileCommander
      .addFile(absolutePath)
      .map(
        ret ->
          Tuple.of(
            ret.getT1(),
            ret.getT2()
              .doOnNext(
                res -> {
                  if (res) {
                    watcher.watchPath(absolutePath);
                  }
                })));
  }

  @Override
  public Observable<Boolean> removeFile(final Path absolutePath) {
    return fileCommander.removeFile(absolutePath);
  }

  @Override
  public Observable<Boolean> deleteFile(final Path absolutePath) {
    return fileCommander.deleteFile(absolutePath);
  }

  @Override
  public Observable<Boolean> updateFile(final Path absolutePath) {
    return fileCommander.updateFile(absolutePath);
  }
}
