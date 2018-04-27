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
package ubicrypt.core.fdx;

import com.google.common.collect.Iterators;

import org.slf4j.Logger;

import java.util.Optional;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.error;
import static rx.Observable.just;
import static ubicrypt.core.Utils.dig;

public class FDXSaver implements Observable.OnSubscribe<Optional<RemoteFile>> {
  private static final Logger log = getLogger(FDXSaver.class);
  private final IPersist serializer;
  private final FileIndex index;
  private final RemoteFile file;

  public FDXSaver(IPersist serializer, FileIndex index) {
    this.serializer = serializer;
    this.index = index;
    file = null;
  }

  public FDXSaver(IPersist serializer, FileIndex index, RemoteFile file) {
    checkNotNull(serializer, "serializer must not be null");
    checkNotNull(index, "index must not be null");
    this.serializer = serializer;
    this.index = index;
    this.file = file;
  }

  @Override
  public void call(Subscriber<? super Optional<RemoteFile>> subscriber) {
    save(Iterators.getLast(index.iterator())).last().subscribe(subscriber);
  }

  private Observable<Optional<RemoteFile>> save(FileIndex ind) {
    boolean tocreate =
        (ind.getParent() == null && file == null)
            || (ind.getParent() == null && !dig(file, RemoteFile::getRemoteName).isPresent())
            || (ind.getParent() != null
                && !dig(
                        ind,
                        FileIndex::getParent,
                        FileIndex::getNextIndex,
                        RemoteFile::getRemoteName)
                    .isPresent());
    RemoteFile rfile;
    if (tocreate) {
      rfile = new RemoteFile();
      ind.setStatus(Action.add);
    } else {
      rfile = (ind.getParent() == null && file != null) ? file : ind.getParent().getNextIndex();
    }
    FileIndex parent = ind.getParent();
    switch (ind.getStatus()) {
      case add:
        return serializer
            .put(ind, rfile)
            .flatMap(
                r -> {
                  if (parent == null) {
                    return just(Optional.of(rfile));
                  }
                  if (parent.getStatus() != Action.add) {
                    parent.setStatus(Action.update);
                  }
                  parent.setNextIndex(rfile);
                  return save(parent);
                });
      case update:
        return serializer
            .put(ind, rfile)
            .flatMap(
                r -> {
                  if (parent == null) {
                    return just(Optional.empty());
                  }
                  return save(parent);
                });
      case delete:
        return serializer
            .delete(rfile)
            .flatMap(
                r -> {
                  if (parent == null) {
                    return just(Optional.empty());
                  }
                  if (ind.getNextIndex() != null) {
                    parent.setNextIndex(ind.getNextIndex());
                    if (parent.getStatus() != Action.add) {
                      parent.setStatus(Action.update);
                    }
                    return save(parent);
                  }
                  parent.setNextIndex(null);
                  parent.setNext(null);
                  if (parent.getStatus() != Action.add) {
                    parent.setStatus(Action.update);
                  }
                  return save(parent);
                });
      case unchanged:
        if (parent == null) {
          return just(Optional.empty());
        }
        return save(parent);
      default:
        log.warn("status {} not managed", ind.getStatus());
        return error(new RuntimeException("status:" + ind.getStatus() + " not managed"));
    }
  }
}
