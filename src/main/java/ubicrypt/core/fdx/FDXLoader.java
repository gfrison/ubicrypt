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

import java.util.Objects;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;

import static rx.Observable.create;
import static rx.Observable.just;

public class FDXLoader implements Observable.OnSubscribe<FileIndex> {
  private final IPersist serializer;
  private final RemoteFile fileIndexFile;
  private final FileIndex parent;

  public FDXLoader(IPersist serializer, RemoteFile fileIndexFile) {
    Objects.nonNull(fileIndexFile);
    this.serializer = serializer;
    this.fileIndexFile = fileIndexFile;
    this.parent = null;
  }

  private FDXLoader(IPersist serializer, RemoteFile fileIndexFile, FileIndex parent) {
    this.serializer = serializer;
    this.fileIndexFile = fileIndexFile;
    this.parent = parent;
  }

  @Override
  public void call(Subscriber<? super FileIndex> subscriber) {
    serializer.getObject(fileIndexFile, FileIndex.class)
        .flatMap(fi -> {
          if (parent != null) {
            fi.setParent(parent);
            parent.setNext(fi);
          }
          if (fi.getNextIndex() != null) {
            return just(fi).concatWith(create(new FDXLoader(serializer, fi.getNextIndex(), fi)));
          } else {
            return Observable.just(fi);
          }
        })
        .toList()
        .map(l->l.iterator().next())
        .subscribe(subscriber);
  }
}
