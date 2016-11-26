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

import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.functions.Actions;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.exp.AlreadyManagedException;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.util.PGPKValue;

import static rx.Observable.error;

public class ProviderCommander {
    private static final Logger log = LoggerFactory.getLogger(ProviderCommander.class);
    @Inject
    LocalConfig localConfig;
    @Resource
    @Qualifier("providerLifeCycle")
    private ProviderLifeCycle providerLifeCycle;
    @Resource
    @Qualifier("providerEvent")
    private Subject<ProviderEvent, ProviderEvent> providerEvents = BufferUntilSubscriber.create();


    public Observable<Boolean> register(final UbiProvider provider) {
        return Observable.create(subscriber -> {
            log.info("registering provider:{}", provider);
            if (localConfig.getProviders().stream().filter(provider::equals).findFirst().isPresent()) {
                log.info("provider:{} already present", provider);
                subscriber.onError(new AlreadyManagedException(provider));
                return;
            }
            providerLifeCycle.activateProvider(provider).doOnCompleted(() -> {
                localConfig.getProviders().add(provider);
                subscriber.onNext(true);
                providerLifeCycle.enabledProviders().stream()
                        .filter(hook -> !hook.getProvider().equals(provider))
                        .forEach(hook -> hook.getConfigSaver().apply(rconf -> {
                            //add provider
                            rconf.getProviders().add(provider);
                            return rconf;
                        }));
            }).subscribe(Actions.empty(), subscriber::onError, subscriber::onCompleted);
        });
    }

    public Observable<Boolean> remove(final UbiProvider provider) {
        try {
            log.info("removing provider:{}", provider);
            if (!localConfig.getProviders().remove(provider)) {
                return error(new NotFoundException(provider));
            }
            providerLifeCycle.enabledProviders().stream()
                    .filter(hook -> !hook.getProvider().equals(provider))
                    .forEach(hook -> hook.getConfigSaver().apply(rconf -> {
                        //remove provider
                        rconf.getProviders().remove(provider);
                        return rconf;
                    }));
            return providerLifeCycle.deactivateProvider(provider);
        } catch (Exception e) {
            return error(e);
        }
    }

    public Observable<Boolean> addOwnedPK(final PGPPublicKey pgpPublicKey) {
        localConfig.getOwnedPKs().add(new PGPKValue(pgpPublicKey));
        return Observable.merge(providerLifeCycle.enabledProviders().stream()
                .map(ProviderHook::getConfLockRewriter)
                .map(Observable::create)
                .collect(Collectors.toList()));
    }
}
