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
package ubicrypt.core.watch;

import org.slf4j.Logger;

import java.nio.file.Path;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.IFileCommander;
import ubicrypt.core.exp.AlreadyManagedException;
import ubicrypt.core.provider.FileEvent;

import static org.slf4j.LoggerFactory.getLogger;

public class WatchReactor {
  private static final Logger log = getLogger(WatchReactor.class);
  @Inject
  PublishSubject<PathEvent> pathStream;
  @Inject
  IFileCommander fileCommander;
  @Inject
  Path basePath;
  @Resource
  private Subject<FileEvent, FileEvent> fileEvents;
  @Inject
  private WatcherBroadcaster watcherBroadcaster;

  @PostConstruct
  public void init() {
    //react to file incoming/removing local files
    fileEvents
      .filter(fileEvent -> fileEvent.getLocation() == FileEvent.Location.local)
      .subscribe(
        fileEvent -> {
          switch (fileEvent.getType()) {
            case created:
              watcherBroadcaster.watchPath(basePath.resolve(fileEvent.getFile().getPath()));
              break;
            case deleted:
            case removed:
              //TODO
          }
        });
    //react to filesystem changes
    pathStream.subscribe(
      pathEvent -> {
        log.debug("incoming {}", pathEvent);
        try {
          switch (pathEvent.getEvent()) {
            case create:
              fileCommander
                .addFile(pathEvent.getPath())
                .flatMap(Tuple2::getT2)
                .onErrorResumeNext(
                  err -> {
                    if (err instanceof AlreadyManagedException) {
                      return fileCommander.updateFile(pathEvent.getPath());
                    }
                    return Observable.error(err);
                  })
                .subscribe(
                  res -> log.info("add/update file:{}, result:{}", pathEvent.getPath(), res),
                  err -> log.error("error event:", pathEvent, err));
              break;
            case update:
              fileCommander
                .updateFile(pathEvent.getPath())
                .subscribe(
                  res -> log.info("update file:{}, result:{}", pathEvent.getPath(), res),
                  err -> log.error("error event:", pathEvent, err));
              break;
            case delete:
              fileCommander
                .deleteFile(pathEvent.getPath())
                .subscribe(
                  res -> log.info("remove file:{}, result:{}", pathEvent.getPath(), res),
                  err -> log.error("error event:", pathEvent, err));
              break;
          }
        } catch (final Exception e) {
          log.error("error on path event:{}", pathEvent.getPath(), e);
        }
      },
      err -> log.error(err.getMessage(), err));
    log.info("file change reactor started");
  }
}
