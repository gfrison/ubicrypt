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

import rx.Observable;
import rx.Subscriber;

public class JustOnSubscribe<T> implements Observable.OnSubscribe<T> {
  final T value;

  public JustOnSubscribe(T value) {
    this.value = value;
  }

  @Override
  public void call(Subscriber<? super T> s) {
    s.onNext(value);
    s.onCompleted();
  }
}
