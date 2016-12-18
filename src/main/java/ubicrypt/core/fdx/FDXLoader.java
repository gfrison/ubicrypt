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

import java.util.List;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IObjectSerializer;

import static rx.Observable.create;
import static rx.Observable.empty;

public class FDXLoader implements Observable.OnSubscribe<Tuple2<RemoteFile, List<RemoteFile>>> {
  private final IObjectSerializer serializer;
  private final RemoteFile fileIndexFile;

  public FDXLoader(IObjectSerializer serializer, RemoteFile fileIndexFile) {
    this.serializer = serializer;
    this.fileIndexFile = fileIndexFile;
  }

  @Override
  public void call(Subscriber<? super Tuple2<RemoteFile, List<RemoteFile>>> subscriber) {
    serializer
        .getObject(fileIndexFile, FileIndex.class)
        .flatMap(
            fdx -> {
              subscriber.onNext(Tuple.of(fileIndexFile, fdx.getFiles()));
              final RemoteFile nextIndex = fdx.getNextIndex();
              if (nextIndex != null) {
                return create(new FDXLoader(serializer, nextIndex));
              }
              return empty();
            })
        .subscribe(subscriber);
  }
}
