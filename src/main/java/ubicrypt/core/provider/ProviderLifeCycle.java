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
package ubicrypt.core.provider;

import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.Subscription;
import rx.functions.Actions;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.ProviderLock;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.lock.ConfigAcquirer;
import ubicrypt.core.provider.lock.InitLockChecker;
import ubicrypt.core.provider.lock.LockChecker;
import ubicrypt.core.provider.lock.ObjectIO;
import ubicrypt.core.remote.OnErrorRemote;
import ubicrypt.core.remote.OnInsertRemote;
import ubicrypt.core.remote.OnUpdateRemote;
import ubicrypt.core.remote.RemoteRepository;
import ubicrypt.core.util.InProgressTracker;
import ubicrypt.core.util.ObjectSerializer;

import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.create;
import static rx.Observable.error;
import static rx.Observable.just;
import static ubicrypt.core.Utils.springIt;

public class ProviderLifeCycle implements ApplicationContextAware {
    private static final Logger log = getLogger(ProviderLifeCycle.class);
    final Map<ProviderHook, Subscription> providerListeners = new ConcurrentHashMap<>();
    final Map<ProviderHook, AtomicBoolean> statusProvider = new ConcurrentHashMap<>();
    final List<ProviderHook> currentlyActiveProviders = new CopyOnWriteArrayList<>();
    @Resource
    @Qualifier("providerEvent")
    private Subject<ProviderEvent, ProviderEvent> providerEvents = BufferUntilSubscriber.create();
    @Inject
    private LocalConfig localConfig;
    @Inject
    private int deviceId;
    private ConfigurableApplicationContext ctx;
    @Inject
    private InProgressTracker inProgressTracker;

    @PostConstruct
    public void init() {
        log.info("init providers");
        localConfig.getProviders().stream().forEach(provider -> initializeProvider(provider).subscribe(Actions.empty(), err -> log.error("error on initializing provider:{}", provider, err)));
    }


    public Observable<Boolean> initializeProvider(UbiProvider provider) {
        return create(subscriber -> {
            try {
                ObjectSerializer serializer = springIt(ctx, new ObjectSerializer(provider));
                ObjectIO<ProviderLock> lockIO = new ObjectIO(serializer, provider.getLockFile(), ProviderLock.class);
                LockChecker lockCheker = new LockChecker(deviceId, lockIO, lockIO, provider.getDurationLockMs(), provider.getDelayAcquiringLockMs());
                /**
                 * renew lock when download/upload in progress
                 */
                lockCheker.setShouldExtendLock(() -> inProgressTracker.inProgress());
                ObjectIO<RemoteConfig> configIO = new ObjectIO<>(serializer, provider.getConfFile(), RemoteConfig.class);
                ConfigAcquirer acquirer = new ConfigAcquirer(new InitLockChecker(provider, deviceId), lockCheker, configIO);
                acquirer.setProviderRef(provider.toString());
                RemoteRepository repository = springIt(ctx, new RemoteRepository(acquirer, provider, configIO));
                repository.setActions(Arrays.asList(springIt(ctx, new OnUpdateRemote(provider, repository)),
                        springIt(ctx, new OnInsertRemote(provider, repository)),
                        springIt(ctx, new OnErrorRemote(provider, repository))));
                ProviderHook hook = new ProviderHook(provider, acquirer, repository);
                hook.setConfigSaver(new ProviderConfSaver(acquirer, configIO));
                hook.setStatusEvents(acquirer.getStatuses());
                hook.setConfLockRewriter(new RewriteConfLock(configIO, lockIO));
                //close provider when expired
                acquirer.getStatuses().filter(status -> status == ProviderStatus.expired).subscribe(status -> provider.close());
                //broadcast events for this provider
                acquirer.getStatuses().map(status -> new ProviderEvent(status, hook)).subscribe(providerEvents);
                final AtomicBoolean active = new AtomicBoolean(false);
                statusProvider.put(hook, active);
                providerListeners.put(hook, hook.getStatusEvents().subscribe(status -> {
                    active.set(status != ProviderStatus.error);
                    if (status == ProviderStatus.active) {
                        currentlyActiveProviders.add(hook);
                    } else {
                        currentlyActiveProviders.remove(hook);
                    }
                }));
                create(acquirer).subscribe(releaser -> {
                    releaser.getReleaser().call();
                    subscriber.onNext(true);
                }, err -> {
                    log.error("error on provider:{}", provider);
                    subscriber.onError(err);
                }, subscriber::onCompleted);
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    public Observable<Boolean> activateProvider(UbiProvider provider) {
        Optional<Map.Entry<ProviderHook, AtomicBoolean>> first = statusProvider.entrySet().stream()
                .filter(entry -> entry.getKey().getProvider().equals(provider))
                .findFirst();
        if (!first.isPresent()) {
            return just(false);
        }
        return create(first.get().getKey().getAcquirer())
                .map(acquirerReleaser -> {
                    acquirerReleaser.getReleaser().call();
                    return true;
                })
                .defaultIfEmpty(false);

    }

    public Observable<Boolean> deactivateProvider(UbiProvider provider) {
        try {
            ProviderHook hook = statusProvider.keySet().stream().filter(hk -> hk.getProvider().equals(provider)).findFirst().orElseThrow(() -> new NotFoundException(provider));
            providerListeners.remove(hook).unsubscribe();
            statusProvider.remove(hook);
            return just(true);
        } catch (Exception e) {
            return error(e);
        }
    }

    public List<ProviderHook> enabledProviders() {
        return statusProvider.entrySet().stream().filter(entry -> entry.getValue().get()).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public List<ProviderHook> currentlyActiveProviders() {
        return currentlyActiveProviders.stream().collect(Collectors.toList());
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = (ConfigurableApplicationContext) applicationContext;
    }

}
