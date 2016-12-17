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
package ubicrypt.core.remote;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.InputStream;

import javax.annotation.Resource;

import rx.functions.Action0;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.MonitorInputStream;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.UbiProvider;

import static org.slf4j.LoggerFactory.getLogger;

public abstract class RemoteAction implements IRemoteAction {
  private static final Logger log = getLogger(RemoteAction.class);
  protected final UbiProvider provider;
  protected final IRepository repository;

  @Resource
  @Qualifier("progressEvents")
  protected PublishSubject<ProgressFile> progressEvents;

  @Resource
  @Qualifier("fileEvents")
  protected Subject<FileEvent, FileEvent> fileEvents;

  protected RemoteAction(UbiProvider provider, IRepository repository) {
    this.provider = provider;
    this.repository = repository;
  }

  Action0 fileEvents(final FileProvenience fp, final FileEvent.Type fileEventType) {
    return () ->
      fileEvents.onNext(new FileEvent(fp.getFile(), fileEventType, FileEvent.Location.remote));
  }

  InputStream monitor(final FileProvenience fp, final InputStream inputStream) {
    final MonitorInputStream mis = new MonitorInputStream(inputStream);
    mis.monitor()
      .subscribe(
        chunk -> progressEvents.onNext(new ProgressFile(fp, repository, chunk)),
        err -> {
          log.error(err.getMessage(), err);
          progressEvents.onNext(new ProgressFile(fp, repository, false, true));
        },
        () -> {
          log.debug("send complete progress file:{}", fp.getFile().getPath());
          progressEvents.onNext(new ProgressFile(fp, repository, true, false));
        });
    return mis;
  }

  public void setProgressEvents(PublishSubject<ProgressFile> progressEvents) {
    this.progressEvents = progressEvents;
  }

  public void setFileEvents(Subject<FileEvent, FileEvent> fileEvents) {
    this.fileEvents = fileEvents;
  }
}
