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
package ubicrypt.core.provider.lock;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IObjectSerializer;

public class ObjectIO<T> implements RemoteIO<T> {
  private final IObjectSerializer serializer;
  private final RemoteFile rfile;
  private final Class<T> type;

  public ObjectIO(IObjectSerializer serializer, RemoteFile rfile, Class<T> type) {
    this.serializer = serializer;
    this.rfile = rfile;
    this.type = type;
  }

  @Override
  public Observable<Boolean> apply(T t) {
    return serializer.put(t, rfile);
  }

  @Override
  public void call(Subscriber<? super T> subscriber) {
    serializer.getObject(rfile, type).subscribe(subscriber);
  }
}
