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

import org.slf4j.Logger;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static org.slf4j.LoggerFactory.getLogger;

public class InitLockChecker implements Observable.OnSubscribe<ProviderStatus> {
  private static final Logger log = getLogger(InitLockChecker.class);
  private final UbiProvider provider;
  private final long deviceId;

  public InitLockChecker(UbiProvider provider, int deviceId) {
    this.provider = provider;
    this.deviceId = deviceId;
  }

  @Override
  public void call(Subscriber<? super ProviderStatus> subscriber) {
    provider
        .init(deviceId)
        .doOnNext(status -> log.info("initialize {}, status:{}", provider, status))
        .subscribe(subscriber);
  }
}
