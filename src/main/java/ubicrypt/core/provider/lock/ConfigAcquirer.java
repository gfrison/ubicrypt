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

import org.slf4j.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.just;
import static ubicrypt.core.provider.ProviderStatus.*;


public class ConfigAcquirer implements Observable.OnSubscribe<AcquirerReleaser> {
    private static final Logger log = getLogger(ConfigAcquirer.class);
    private final Observable.OnSubscribe<LockStatus> lockChecker;
    private final RemoteIO<RemoteConfig> remoteIO;
    private final Subject<ProviderStatus, ProviderStatus> statusStream = PublishSubject.create();
    private final Observable<ProviderStatus> statuses = statusStream.share();
    AtomicLong inprogress = new AtomicLong(0);
    private ProviderStatus status = unitialized;
    private RemoteConfig config;
    private String provider = "";

    public ConfigAcquirer(Observable.OnSubscribe<LockStatus> lockChecker, RemoteIO<RemoteConfig> remoteIO) {
        this.lockChecker = lockChecker;
        this.remoteIO = remoteIO;
        statuses.subscribe(st -> log.debug("incoming status:{}", st));
    }


    @Override
    public void call(Subscriber<? super AcquirerReleaser> subscriber) {
        log.trace("status:{}", status);
        switch (status) {
            case unitialized:
            case expired:
                Observable.create(lockChecker).subscribe(new LockSubscriber(subscriber));
            case initialized:
                log.debug("wait for filter");
                statuses.filter(event -> event == error || event == unavailable || event == active).flatMap(event -> {
                    if (event == error || event == unavailable) {
                        log.debug("return no configuration");
                        return just(null);
                    }
                    //active
                    inprogress.incrementAndGet();
                    return just(new AcquirerReleaser(config, () -> inprogress.decrementAndGet()));
                }).firstOrDefault(null).filter(Objects::nonNull).subscribe(subscriber);
                break;
            case active:
                inprogress.incrementAndGet();
                subscriber.onNext(new AcquirerReleaser(config, () -> inprogress.decrementAndGet()));
                subscriber.onCompleted();
                break;
            case unavailable:
            case error:
                subscriber.onCompleted();
                break;
            default:
                log.warn("unmanaged status:{}", status);
        }
    }

    private void setStatus(ProviderStatus status) {
        this.status = status;
    }

    private void setConfig(RemoteConfig config) {
        this.config = config;
    }

    public void setProviderRef(String provider) {
        this.provider = provider;
    }

    public Observable<ProviderStatus> getStatuses() {
        return statuses;
    }

    class LockSubscriber extends Subscriber<LockStatus> {
        private final Subscriber<? super AcquirerReleaser> subscriber;

        public LockSubscriber(Subscriber<? super AcquirerReleaser> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            log.error("provider:{}, error acquiring lock", provider, e);
            setStatus(error);
            subscriber.onError(e);
            statusStream.onNext(error);
//            call.onNext(status);
        }

        @Override
        public void onNext(LockStatus lockStatus) {
            log.debug("lock status:{}", lockStatus);
            switch (lockStatus) {
                case available:
                    setStatus(initialized);
                    statusStream.onNext(initialized);
                    Observable.create(remoteIO)
                            .onErrorResumeNext(err -> {
                                if (err instanceof NotFoundException) {
                                    return remoteIO.apply(new RemoteConfig())
                                            .map(res -> new RemoteConfig());
                                }
                                return Observable.error(err);
                            })
                            .subscribe(config -> {
                                log.debug("config arrived:{}", config);
                                setConfig(config);
                                setStatus(active);
                            }, err -> {
                                log.error("provider:{} failed to get config file", provider, err);
                                setStatus(error);
                                statusStream.onNext(error);
                            }, () -> {
                                log.debug("remoteIO get completed");
                                statusStream.onNext(active);
                            });

                    break;
                case unavailable:
                    setStatus(unavailable);
                    statusStream.onNext(unavailable);
                    break;
                case expired:
                    setStatus(expired);
                    statusStream.onNext(expired);
                    break;
                default:
                    log.error("unexpected status:{}", lockStatus);
            }
        }
    }
}
