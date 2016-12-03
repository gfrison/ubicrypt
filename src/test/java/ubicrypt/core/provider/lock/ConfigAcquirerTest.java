/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.core.provider.lock;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import ubicrypt.core.JustOnSubscribe;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.provider.ProviderStatus.initialized;

public class ConfigAcquirerTest {
    private static final Logger log = getLogger(ConfigAcquirerTest.class);
    private ConfigAcquirer acquirer;
    private PublishSubject<LockStatus> lockPub;
    private PublishSubject<RemoteConfig> configPub;
    private CountDownLatch cd;
    private AtomicBoolean saveConfigResult;

    @Before
    public void setUp() throws Exception {
        cd = new CountDownLatch(1);
        lockPub = PublishSubject.create();
        configPub = PublishSubject.create();
        Observable.OnSubscribe<LockStatus> lockChecker = subscriber -> {
            log.debug("lockchecked subscribed");
            lockPub.subscribe(subscriber);
        };
        saveConfigResult = new AtomicBoolean(true);
        RemoteIO<RemoteConfig> configIO = new RemoteIO<RemoteConfig>() {
            @Override
            public Observable<Boolean> apply(RemoteConfig remoteConfig) {
                log.debug("configIO.put");
                return Observable.just(saveConfigResult.get());
            }

            @Override
            public void call(Subscriber<? super RemoteConfig> subscriber) {
                log.debug("configIO.get");
                configPub.subscribe(subscriber);
            }
        };
        acquirer = new ConfigAcquirer(new JustOnSubscribe(ProviderStatus.initialized), lockChecker, configIO);
    }

    @Test
    public void success() throws Exception {
        Observable.create(acquirer).doOnNext(config -> assertThat(config).isNotNull()).subscribe(config -> cd.countDown());
        lockPub.onNext(LockStatus.available);
        configPub.onNext(new RemoteConfig());
        configPub.onCompleted();
        assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void lockUnavailable() throws Exception {
        Observable.create(acquirer).doOnNext(n -> fail("should not send anything")).doOnCompleted(() -> cd.countDown()).subscribe();
        lockPub.onNext(LockStatus.unavailable);
        configPub.onNext(new RemoteConfig());
        configPub.onCompleted();
        assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void lockExpired() throws Exception {
        success();
        lockPub.onNext(LockStatus.expired);
        Observable.create(acquirer).doOnNext(config -> assertThat(config).isNotNull()).subscribe(config -> cd.countDown());
        Thread.sleep(10);
        lockPub.onNext(LockStatus.available);
        configPub.onNext(new RemoteConfig());
        configPub.onCompleted();
        assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void createNewRemoteConfig() throws Exception {
        Observable.create(acquirer).doOnNext(config -> assertThat(config).isNotNull()).subscribe(config -> cd.countDown());
        saveConfigResult.set(true);
        lockPub.onNext(LockStatus.available);
        configPub.onError(new NotFoundException(""));
        assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void progress() throws Exception {
        AtomicReference<AcquirerReleaser> ref = new AtomicReference<>();
        Observable.create(acquirer).doOnNext(ref::set).subscribe(config -> cd.countDown());
        lockPub.onNext(LockStatus.available);
        configPub.onNext(new RemoteConfig() {{
            setProviders(ImmutableSet.of(mock(UbiProvider.class)));
        }});
        configPub.onCompleted();
        assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ref.get()).isNotNull();
        assertThat(ref.get().getRemoteConfig().getProviders()).hasSize(1);
        assertThat(acquirer.inprogress.get()).isEqualTo(1);
        ref.get().getReleaser().call();
        assertThat(acquirer.inprogress.get()).isEqualTo(0);


    }
}
