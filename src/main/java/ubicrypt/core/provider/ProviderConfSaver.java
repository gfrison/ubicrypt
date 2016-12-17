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

import java.util.function.Function;

import rx.Observable;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.provider.lock.AcquirerReleaser;

import static rx.Observable.create;

public class ProviderConfSaver
  implements Function<Function<RemoteConfig, RemoteConfig>, Observable<Boolean>> {
  private final Observable.OnSubscribe<AcquirerReleaser> acquirer;
  private final RemoteIO<RemoteConfig> configIO;

  public ProviderConfSaver(
    Observable.OnSubscribe<AcquirerReleaser> acquirer, RemoteIO<RemoteConfig> configIO) {
    this.acquirer = acquirer;
    this.configIO = configIO;
  }

  @Override
  public Observable<Boolean> apply(Function<RemoteConfig, RemoteConfig> function) {
    return create(acquirer)
      .flatMap(
        releaser ->
          configIO
            .apply(function.apply(releaser.getRemoteConfig()))
            .doOnError(err -> releaser.getReleaser().call())
            .doOnCompleted(() -> releaser.getReleaser().call()));
  }
}
