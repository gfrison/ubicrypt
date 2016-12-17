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
package ubicrypt.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import rx.Observable;
import rx.Subscriber;

public class OnSubscribeInputStream implements Observable.OnSubscribe<byte[]> {
  private final InputStream is;
  private final int bufferSize;

  public OnSubscribeInputStream(final InputStream inputStream, final int bufferSize) {
    this.is = inputStream;
    this.bufferSize = bufferSize;
  }

  @Override
  public void call(final Subscriber<? super byte[]> subscriber) {
    final byte[] buf = new byte[bufferSize];
    try {
      int len = is.read(buf);
      while (len != -1 && !subscriber.isUnsubscribed()) {
        subscriber.onNext(Arrays.copyOfRange(buf, 0, len));
        len = is.read(buf);
      }
      if (!subscriber.isUnsubscribed()) {
        subscriber.onCompleted();
      }
    } catch (final Exception e) {
      subscriber.onError(e);
    } finally {
      try {
        is.close();
      } catch (final IOException e1) {

      }
    }
  }
}
