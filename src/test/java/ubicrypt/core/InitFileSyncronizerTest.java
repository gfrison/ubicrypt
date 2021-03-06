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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import rx.subjects.PublishSubject;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderStatus;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

public class InitFileSyncronizerTest {

  @Test
  public void providerStatusChange() throws Exception {
    AtomicInteger invokedCounter = new AtomicInteger(0);
    InitFileSyncronizer fileSyncronizer = new InitFileSyncronizer();
    final PublishSubject<ProviderEvent> providerEvent = PublishSubject.create();
    fileSyncronizer.setProviderEvent(providerEvent);
    fileSyncronizer.setFileSynchronizer(
        subscriber -> {
          invokedCounter.incrementAndGet();
          subscriber.onNext(true);
          subscriber.onCompleted();
        });
    fileSyncronizer.init();

    providerEvent.onNext(new ProviderEvent(ProviderStatus.initialized, null));
    sleep(50);
    assertThat(invokedCounter.get()).isEqualTo(0);
    providerEvent.onNext(new ProviderEvent(ProviderStatus.active, null));
    sleep(50);
    assertThat(invokedCounter.get()).isEqualTo(1);
    providerEvent.onNext(new ProviderEvent(ProviderStatus.expired, null));
    sleep(50);
    assertThat(invokedCounter.get()).isEqualTo(1);
    providerEvent.onNext(new ProviderEvent(ProviderStatus.initialized, null));
    sleep(50);
    assertThat(invokedCounter.get()).isEqualTo(1);
    providerEvent.onNext(new ProviderEvent(ProviderStatus.active, null));
    sleep(50);
    assertThat(invokedCounter.get()).isEqualTo(2);
  }
}
