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

import org.apache.commons.lang3.StringUtils;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static rx.Observable.create;
import static rx.Observable.empty;

public class FDXLoader implements Observable.OnSubscribe<FileIndex> {
  private final IPersist serializer;
  private final RemoteFile fileIndexFile;

  public FDXLoader(IPersist serializer, RemoteFile fileIndexFile) {
    this.serializer = serializer;
    this.fileIndexFile = fileIndexFile;
  }

  @Override
  public void call(Subscriber<? super FileIndex> subscriber) {
    if (isEmpty(fileIndexFile.getName())) {
      subscriber.onCompleted();
      return;
    }
    serializer
        .getObject(fileIndexFile, FileIndex.class)
        .flatMap(
            fdx -> {
              subscriber.onNext(fdx);
              final RemoteFile nextIndex = fdx.getNextIndex();
              if (nextIndex != null && isNotEmpty(nextIndex.getName())) {
                return create(new FDXLoader(serializer, nextIndex));
              }
              return empty();
            })
        .subscribe(subscriber);
  }
}
