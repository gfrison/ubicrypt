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

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;
import rx.functions.Func1;
import rx.functions.Func2;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.lock.AcquirerReleaser;

import static com.google.common.base.Preconditions.checkNotNull;
import static rx.Observable.create;

public class RemoteFileGetter
    implements Func1<UbiFile, Observable<InputStream>>,
        Func2<UbiFile, BiFunction<RemoteFile, InputStream, InputStream>, Observable<InputStream>> {
  private final Observable.OnSubscribe<AcquirerReleaser> acquirer;
  private final UbiProvider provider;

  public RemoteFileGetter(Observable.OnSubscribe<AcquirerReleaser> acquirer, UbiProvider provider) {
    checkNotNull(acquirer, "acquirer not null");
    checkNotNull(provider, "provider not null");
    this.acquirer = acquirer;
    this.provider = provider;
  }

  private static Predicate<RemoteFile> filterRemote(final UbiFile file) {
    return (rfile) -> rfile.equals(file);
  }

  private static Func1<? super RemoteConfig, RemoteFile> remoteFile(final UbiFile file) {
    return remoteConfig ->
        remoteConfig
            .getRemoteFiles()
            .stream()
            .filter(filterRemote(file))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("not present in remote file list"));
  }

  @Override
  public Observable<InputStream> call(UbiFile ubiFile) {
    return call(ubiFile, (rfile, is) -> is);
  }

  @Override
  public Observable<InputStream> call(
      UbiFile file, BiFunction<RemoteFile, InputStream, InputStream> streamTransformer) {
    AtomicReference<Action0> releaser = new AtomicReference<>();
    return create(acquirer)
        .doOnNext(acquirerReleaser -> releaser.set(acquirerReleaser.getReleaser()))
        .map(AcquirerReleaser::getRemoteConfig)
        .map(remoteFile(file))
        .flatMap(rf -> provider.get(rf.getName()).map(is -> streamTransformer.apply(rf, is)))
        .doOnCompleted(releaser.get() != null ? releaser.get()::call : Actions.empty())
        .doOnError(releaser.get() != null ? err -> releaser.get().call() : err -> {});
  }
}
