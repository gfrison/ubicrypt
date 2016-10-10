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
package ubicrypt.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderStatus;

import static ubicrypt.core.provider.ProviderStatus.active;
import static ubicrypt.core.provider.ProviderStatus.added;
import static ubicrypt.core.provider.ProviderStatus.error;
import static ubicrypt.core.provider.ProviderStatus.initialized;
import static ubicrypt.core.provider.ProviderStatus.removed;
import static ubicrypt.core.provider.ProviderStatus.uninitialized;

/**
 * take all local and remote files and synchronized them all at startup.
 */
public class InitFileSyncronizer {
    private static final Logger log = LoggerFactory.getLogger(InitFileSyncronizer.class);
    private final AtomicBoolean enqueued = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    @Resource
    @Qualifier("providerEvent")
    Observable<ProviderEvent> providerEvent;
    @Resource
    @Qualifier("fileSynchronizer")
    Observable.OnSubscribe<Boolean> fileSynchronizer;
    private Action0 onComplete = Actions.empty();
    private static List<ProviderStatus> ignoreStatuses = Arrays.asList(added, uninitialized, initialized, error, removed);

    /**
     * when all provider are not uninitialized, begin the sync for all files
     */
    @PostConstruct
    public void init() {
        log.info("file synchronizer started");
        providerEvent
                .filter(event -> !ignoreStatuses.contains(event.getEvent()))
                .subscribe(event -> {
                    if (event.getEvent() == active) {
                        process(event);
                    }
                });
    }

    private void process(ProviderEvent event) {
        log.info("become active:{}", event);
        if (processing.compareAndSet(false, true)) {
            doSync();
        } else {
            enqueued.set(true);
        }
    }

    private void doSync() {
        Observable.create(fileSynchronizer)
                .doOnCompleted(onComplete)
                .doOnCompleted(() -> {
                    if (enqueued.compareAndSet(true, false)) {
                        doSync();
                    } else {
                        processing.set(false);
                    }
                })
                .doOnError(err -> processing.set(false))
                .subscribe(Actions.empty(), err -> log.error(err.getMessage(), err));
    }

    public void setOnComplete(final Action0 onComplete) {
        this.onComplete = onComplete;
    }

    public void setProviderEvent(Observable<ProviderEvent> providerEvent) {
        this.providerEvent = providerEvent;
    }

    public void setFileSynchronizer(Observable.OnSubscribe<Boolean> fileSynchronizer) {
        this.fileSynchronizer = fileSynchronizer;
    }
}
