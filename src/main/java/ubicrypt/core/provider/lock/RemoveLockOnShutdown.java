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
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.subjects.Subject;
import ubicrypt.core.IStoppable;
import ubicrypt.core.dto.ProviderLock;
import ubicrypt.core.events.ShutdownRegistration;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.util.Persist;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.Utils.springIt;

public class RemoveLockOnShutdown implements IStoppable, ApplicationContextAware {
  private static final Logger log = getLogger(RemoveLockOnShutdown.class);

  @Resource
  @Qualifier("appEvents")
  private Subject appEvents;

  @Inject private ProviderLifeCycle providerLifeCycle;
  @Inject private int deviceId;
  private BeanFactory bf;
  private ConfigurableApplicationContext ctx;

  @PostConstruct
  public void init() {
    appEvents.onNext(new ShutdownRegistration(this));
  }

  @Override
  public Observable<Void> stop() {
    final Stream<ProviderHook> stream = providerLifeCycle.currentlyActiveProviders().stream();
    return Observable.merge(
            stream
                .map(ProviderHook::getProvider)
                .map(
                    provider -> {
                      ProviderLock pl = new ProviderLock(deviceId, Instant.now().minusMillis(1000));
                      Persist serializer = springIt(ctx, new Persist(provider));
                      return serializer
                          .put(pl, provider.getLockFile())
                          .doOnCompleted(() -> log.info("removed lock on provider:{}", provider));
                    })
                .collect(Collectors.toList()))
        .map(bool -> (Void) null)
        .doOnCompleted(() -> log.info("lock remove completed"));
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.ctx = (ConfigurableApplicationContext) applicationContext;
  }
}
