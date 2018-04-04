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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

import rx.Observable;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.remote.RemoteRepository;

import static rx.Observable.create;

public class ProviderHook {
  private static final Logger log = LoggerFactory.getLogger(ProviderHook.class);
  private final UbiProvider provider;
  private final RemoteRepository repository;
  private final Observable.OnSubscribe<AcquirerReleaser> acquirer;
  private Observable<ProviderStatus> statusEvents;
  private Function<Function<RemoteConfig, RemoteConfig>, Observable<Boolean>> configSaver;
  private Observable.OnSubscribe<Boolean> confLockRewriter;

  public ProviderHook(
      final UbiProvider provider,
      final Observable.OnSubscribe<AcquirerReleaser> acquirer,
      final RemoteRepository repository) {
    log.debug("new hook provider:{}, acquirer:{}, repository:{}", provider, acquirer, repository);
    this.provider = provider;
    this.acquirer = acquirer;
    this.repository = repository;
  }

  public Observable<ProviderStatus> getStatusEvents() {
    return statusEvents;
  }

  public void setStatusEvents(Observable<ProviderStatus> statusEvents) {
    this.statusEvents = statusEvents;
  }

  public UbiProvider getProvider() {
    return provider;
  }

  public Function<Function<RemoteConfig, RemoteConfig>, Observable<Boolean>> getConfigSaver() {
    return configSaver;
  }

  public void setConfigSaver(
      Function<Function<RemoteConfig, RemoteConfig>, Observable<Boolean>> configSaver) {
    this.configSaver = configSaver;
  }

  public RemoteRepository getRepository() {
    return repository;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("provider", provider)
        .toString();
  }

  Observable<RemoteConfig> getConfig() {
    return create(acquirer)
        .map(
            releaser -> {
              releaser.getReleaser().call();
              return releaser.getRemoteConfig();
            });
  }

  public Observable.OnSubscribe<AcquirerReleaser> getAcquirer() {
    return acquirer;
  }

  public Observable.OnSubscribe<Boolean> getConfLockRewriter() {
    return confLockRewriter;
  }

  public void setConfLockRewriter(Observable.OnSubscribe<Boolean> confLockRewriter) {
    this.confLockRewriter = confLockRewriter;
  }
}
