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

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Reverser;

import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.empty;

public class FDXSaver implements Observable.OnSubscribe<RemoteFile> {
  private static final Logger log = getLogger(FDXSaver.class);
  private final IPersist serializer;
  private final FileIndex index;

  public FDXSaver(IPersist serializer, FileIndex index) {
    this.serializer = serializer;
    this.index = index;
  }

  @Override
  public void call(Subscriber<? super RemoteFile> subscriber) {
    //reverse list
    List<FileIndex> list = StreamSupport.stream(index.spliterator(), false).collect(new Reverser<>());

    //save records
    saveCascade(list.iterator(),null);
  }

  private Observable<RemoteFile> saveCascade(
      Iterator<FileIndex> it, RemoteFile chain) {
    if (!it.hasNext()) {
      return empty();
    }
    FileIndex fi = it.next();
    if (chain != null) {
      fi.setNextIndex(chain);
    }
    switch (fi.getStatus()) {
      case add:
        final RemoteFile rf = new RemoteFile();
        return serializer.put(fi, rf)
            .flatMap(res -> saveCascade(it, rf));
      case update:
        return serializer.put(fi, fi.getParent().getNextIndex())
            .flatMap(r -> saveCascade(it, null));
      case delete:
        return serializer.delete(fi.getParent().getNextIndex())
            .flatMap(r -> {
              fi.getParent().setNextIndex(null);
              return saveCascade(it, null);
            });
      case unchanged:
        return saveCascade(it, null);
      default:
        log.warn("no action defined {}", fi.getStatus());
        return empty();
    }
  }


}
