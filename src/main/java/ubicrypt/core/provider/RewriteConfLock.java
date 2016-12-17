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
package ubicrypt.core.provider;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.ProviderLock;
import ubicrypt.core.dto.RemoteConfig;

import static rx.Observable.create;

public class RewriteConfLock implements Observable.OnSubscribe<Boolean> {
  private final RemoteIO<RemoteConfig> confIO;
  private final RemoteIO<ProviderLock> lockIO;

  public RewriteConfLock(RemoteIO<RemoteConfig> confIO, RemoteIO<ProviderLock> lockIO) {
    this.confIO = confIO;
    this.lockIO = lockIO;
  }

  @Override
  public void call(Subscriber<? super Boolean> subscriber) {
    create(confIO)
        .flatMap(confIO::apply)
        .filter(a -> a)
        .flatMap(a -> create(lockIO).flatMap(lockIO::apply))
        .defaultIfEmpty(false)
        .subscribe(subscriber);
  }
}
