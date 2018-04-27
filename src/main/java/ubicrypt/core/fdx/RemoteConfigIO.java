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

import reactor.util.function.Tuples;
import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.StoredRemoteConfig;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.util.IPersist;

import static rx.Observable.create;
import static rx.Observable.just;

public class RemoteConfigIO implements RemoteIO<RemoteConfig> {
  private final IPersist persist;
  private final RemoteIO<StoredRemoteConfig> configIO;

  public RemoteConfigIO(IPersist persist, final RemoteIO<StoredRemoteConfig> configIO) {
    this.persist = persist;
    this.configIO = configIO;
  }

  @Override
  public void call(Subscriber<? super RemoteConfig> subscriber) {
    create(configIO)
        .flatMap(
            stored ->
                create(new FDXLoader(persist, stored.getIndex())).map(fi -> Tuples.of(stored, fi)))
        .subscribe(
            tupla ->
                subscriber.onNext(
                    new RemoteConfig(
                        tupla.getT1().getProviders(),
                        tupla.getT2(),
                        tupla.getT1().getIndex(),
                        100)),
            subscriber::onError,
            subscriber::onCompleted);
  }

  @Override
  public Observable<Boolean> apply(RemoteConfig remoteConfig) {
    StoredRemoteConfig src = new StoredRemoteConfig();
    src.setProviders(remoteConfig.getProviders());
    src.setIndex(remoteConfig.getIndexFile());
    FDXSaver saver = new FDXSaver(persist, remoteConfig.getIndex(), remoteConfig.getIndexFile());
    return create(saver)
        .flatMap(
            rf ->
                rf.map(
                        opt -> {
                          src.setIndex(opt);
                          return configIO.apply(src);
                        })
                    .orElse(just(true)));
  }
}
