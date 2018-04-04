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

import java.util.stream.Collectors;

import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func1;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;

public class FileInSync implements Func1<UbiFile, Observable.OnSubscribe<Boolean>> {

  @Inject ProviderLifeCycle providerLifeCycle;

  public Observable.OnSubscribe<Boolean> call(UbiFile file) {
    return subscriber -> {
      Observable.merge(
              providerLifeCycle
                  .enabledProviders()
                  .stream()
                  .map(ProviderHook::getAcquirer)
                  .map(Observable::create)
                  .collect(Collectors.toList()))
          //return true/false if file is present and up2date within the provider
          .map(
              releaser -> {
                releaser.getReleaser().call();
                RemoteConfig remoteConfig = releaser.getRemoteConfig();
                return remoteConfig.getRemoteFiles().contains(file)
                    && remoteConfig
                        .getRemoteFiles()
                        .stream()
                        .filter(file::equals)
                        .map(rfile -> rfile.compare(file) == VClock.Comparison.equal)
                        .findFirst()
                        .orElse(false);
              })
          .defaultIfEmpty(false)
          .contains(false)
          .map(res -> !res)
          .subscribe(subscriber);
    };
  }
}
