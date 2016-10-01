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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.events.SyncBeginEvent;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.provider.*;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

public class FileSynchronizer implements Observable.OnSubscribe<Boolean> {
    private static final Logger log = getLogger(FileSynchronizer.class);
    final private AtomicReference<Observable<Boolean>> cached = new AtomicReference<>();
    @Resource
    @Qualifier("providerEvent")
    Observable<ProviderEvent> providerEvent;
    @Resource
    @Qualifier("synchProcessing")
    Subject<Boolean, Boolean> synchProcessing;
    @Inject
    LocalConfig localConfig;
    @Inject
    LocalRepository localRepository;
    @Inject
    ProviderLifeCycle providers;
    @Autowired(required = false)
    @Qualifier("appEvents")
    private Subject<Object, Object> appEvents = PublishSubject.create();

    /**
     * return only files which are not in conflict
     *
     * @param all
     * @return
     */
    static Multimap<UUID, FileProvenience> withoutConflicts(final Multimap<UUID, FileProvenience> all) {
        return all.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .filter(fp -> entry.getValue().stream()
                                .filter(fp2 -> fp.getFile().compare(fp2.getFile()) != VClock.Comparison.conflict)
                                .collect(Collectors.toList()).size() == entry.getValue().size())
                        .collect(Collectors.toList()).size() == entry.getValue().size())
                .collect(LinkedHashMultimap::create,
                        (multimap, entry) -> multimap.putAll(entry.getKey(), all.get(entry.getKey())),
                        (m1, m2) -> m1.putAll(m2));
    }

    static HashMap<UUID, FileProvenience> max(final Multimap<UUID, FileProvenience> input) {
        return input.keySet().stream()
                .collect(HashMap::new,
                        (map, uuid) -> map.put(uuid, input.get(uuid).stream().max((f1, f2) -> {
                            switch (f1.getFile().getVclock().compare(f2.getFile().getVclock())) {
                                case newer:
                                    return 1;
                                case older:
                                    return -1;
                                default:
                                    return 0;
                            }
                        }).get()), HashMap::putAll);
    }

    /**
     * return only files which are in conflict
     *
     * @param all
     * @return
     */
    static Multimap<UUID, FileProvenience> conflicts(final Multimap<UUID, FileProvenience> all) {
        return all.asMap().entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .filter(fp -> entry.getValue().stream()
                                .filter(fp2 -> fp.getFile().compare(fp2.getFile()) == VClock.Comparison.conflict)
                                .collect(Collectors.toList()).size() == 0).collect(Collectors.toList()).size() == 0)
                .collect(LinkedHashMultimap::create,
                        (multimap, entry) -> multimap.putAll(entry.getKey(), all.get(entry.getKey())),
                        (m1, m2) -> m1.putAll(m2));
    }

    @Override
    public void call(Subscriber<? super Boolean> subscriber) {
        cached.updateAndGet(cache -> {
            if (cache == null) {
                return create()
                        .doOnSubscribe(() -> {
                            log.info("begin file synchronization... ");
                            appEvents.onNext(new SyncBeginEvent());
                            synchProcessing.onNext(true);
                        })
                        .doOnCompleted(() -> {
                            cached.set(null);
                            appEvents.onNext(new SynchDoneEvent());
                            synchProcessing.onNext(false);
                        }).share();
            }
            return cache;
        }).subscribe(subscriber);
    }

    private Observable<Boolean> create() {
        return packFilesById().flatMap(all -> {
            //conflicting versions
            //TODO: manage conflicts manually
            final Multimap<UUID, FileProvenience> noConflicts = FileSynchronizer.withoutConflicts(all);
            //newer files per id
            final Map<UUID, FileProvenience> max = FileSynchronizer.max(noConflicts);

            //overwrite file to local
            return localChain(max.entrySet().iterator(), Observable.just(true))
                    .flatMap(aVoid ->
                            //copy to all other providers
                            Observable.merge(providers.currentlyActiveProviders().stream()
                                    .map(hook -> providerChain(max.entrySet(), hook))
                                    .collect(Collectors.toList()))
                                    .doOnCompleted(() -> log.info("file synchronization completed")));
        });
    }

    private Observable<Boolean> providerChain(final Set<Map.Entry<UUID, FileProvenience>> entries,
                                              final ProviderHook hook) {
        return Observable.merge(entries.stream()
                .map(entry -> {
                    final IRepository repo = entry.getValue().getFile().isGhost() ? entry.getValue().getOrigin() : localRepository;
                    return hook.getRepository().save(new FileProvenience(entry.getValue().getFile(), repo));
                })
                .collect(Collectors.toList()));
    }

    private Observable<Boolean> localChain(final Iterator<Map.Entry<UUID, FileProvenience>> it,
                                           final Observable<Boolean> observable) {
        if (!it.hasNext()) {
            return observable.defaultIfEmpty(false).last();
        }
        final Map.Entry<UUID, FileProvenience> entry = it.next();
        return localChain(it, observable.flatMap(n -> (entry.getValue().getOrigin().isLocal() || entry.getValue().getFile().isGhost())
                ? Observable.just(true) : localRepository.save(entry.getValue())));
    }

    public Observable<Multimap<UUID, FileProvenience>> packFilesById() {
        List<Observable<Tuple2<ProviderHook, RemoteConfig>>> obconfigs = providers.currentlyActiveProviders().stream().map(provider -> Observable.create(provider.getAcquirer()).map(releaser -> {
            releaser.getReleaser().call();
            return Tuple.of(provider, releaser.getRemoteConfig());
        })).collect(Collectors.toList());
        return Observable.zip(obconfigs, args -> {
            final Multimap<UUID, FileProvenience> all = LinkedHashMultimap.create();
            //add local files
            localConfig.getLocalFiles().stream()
                    .filter(Utils.ignoredFiles)
                    .forEach(file -> all.put(file.getId(), new FileProvenience(file, localRepository)));
            //add all remote files
            Tuple2<ProviderHook, RemoteConfig>[] configs = Arrays.copyOf(args, args.length, Tuple2[].class);
            Stream.of(configs).forEach(config -> config.getT2().getRemoteFiles().forEach(file -> all.put(file.getId(), new FileProvenience(file, config.getT1().getRepository()))));
            return all;
        });
    }


}
