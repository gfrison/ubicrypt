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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.events.SyncBeginEvent;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.remote.RemoteConfig;

import static org.slf4j.LoggerFactory.getLogger;

public class FileSynchronizer implements Observable.OnSubscribe<Boolean> {
  private static final Logger log = getLogger(FileSynchronizer.class);
  private final AtomicReference<Observable<Boolean>> cached = new AtomicReference<>();

  @Resource
  @Qualifier("providerEvent")
  Observable<ProviderEvent> providerEvent;

  @Resource
  @Qualifier("synchProcessing")
  Subject<Boolean, Boolean> synchProcessing;

  @Inject LocalConfig localConfig;
  @Inject LocalRepository localRepository;
  @Inject ProviderLifeCycle providers;

  @Resource
  @Qualifier("fileEvents")
  Subject<FileEvent, FileEvent> fileEvents = PublishSubject.create();

  @Autowired(required = false)
  @Qualifier("appEvents")
  private Subject<Object, Object> appEvents = PublishSubject.create();

  /** return only files which are not in conflict */
  static Multimap<UUID, FileProvenience> withoutConflicts(
      final Multimap<UUID, FileProvenience> all) {
    return all.asMap()
        .entrySet()
        .stream()
        .filter(
            entry ->
                entry
                        .getValue()
                        .stream()
                        .filter(
                            fp ->
                                entry
                                        .getValue()
                                        .stream()
                                        .filter(
                                            fp2 ->
                                                fp.getFile().compare(fp2.getFile())
                                                    != VClock.Comparison.conflict)
                                        .collect(Collectors.toList())
                                        .size()
                                    == entry.getValue().size())
                        .collect(Collectors.toList())
                        .size()
                    == entry.getValue().size())
        .collect(
            LinkedHashMultimap::create,
            (multimap, entry) -> multimap.putAll(entry.getKey(), all.get(entry.getKey())),
            (m1, m2) -> m1.putAll(m2));
  }

  static HashMap<UUID, FileProvenience> max(final Multimap<UUID, FileProvenience> input) {
    return input
        .keySet()
        .stream()
        .collect(
            HashMap::new,
            (map, uuid) ->
                map.put(
                    uuid,
                    input
                        .get(uuid)
                        .stream()
                        .max(
                            (f1, f2) -> {
                              switch (f1.getFile().getVclock().compare(f2.getFile().getVclock())) {
                                case newer:
                                  return 1;
                                case older:
                                  return -1;
                                default:
                                  return 0;
                              }
                            })
                        .get()),
            HashMap::putAll);
  }

  /** return only files which are in conflict */
  static Multimap<UUID, FileProvenience> conflicts(final Multimap<UUID, FileProvenience> all) {
    return all.asMap()
        .entrySet()
        .stream()
        .filter(
            entry ->
                entry
                        .getValue()
                        .stream()
                        .filter(
                            fp ->
                                entry
                                        .getValue()
                                        .stream()
                                        .filter(
                                            fp2 ->
                                                fp.getFile().compare(fp2.getFile())
                                                    == VClock.Comparison.conflict)
                                        .collect(Collectors.toList())
                                        .size()
                                    == 0)
                        .collect(Collectors.toList())
                        .size()
                    == 0)
        .collect(
            LinkedHashMultimap::create,
            (multimap, entry) -> multimap.putAll(entry.getKey(), all.get(entry.getKey())),
            (m1, m2) -> m1.putAll(m2));
  }

  @Override
  public void call(Subscriber<? super Boolean> subscriber) {
    cached
        .updateAndGet(
            cache -> {
              if (cache == null) {
                return create()
                    .doOnSubscribe(
                        () -> {
                          log.info("begin file synchronization... ");
                          appEvents.onNext(new SyncBeginEvent());
                          synchProcessing.onNext(true);
                        })
                    .doOnCompleted(
                        () -> {
                          cached.set(null);
                          appEvents.onNext(new SynchDoneEvent());
                          synchProcessing.onNext(false);
                        })
                    .share();
              }
              return cache;
            })
        .subscribe(subscriber);
  }

  private Observable<Boolean> create() {
    return packFilesById()
        .flatMap(
            all -> {
              //conflicting versions
              //TODO: manage conflicts manually
              final Multimap<UUID, FileProvenience> noConflicts =
                  FileSynchronizer.withoutConflicts(all);
              //newer files per id
              final Map<UUID, FileProvenience> max = FileSynchronizer.max(noConflicts);

              //overwrite file to local
              return localChain(max.entrySet())
                  .flatMap(
                      aVoid ->
                          //copy to all other providers
                          Observable.merge(
                                  providers
                                      .enabledProviders()
                                      .stream()
                                      .map(hook -> providerChain(max.entrySet(), hook))
                                      .collect(Collectors.toList()))
                              .doOnCompleted(() -> log.info("file synchronization completed")));
            });
  }

  private Observable<Boolean> providerChain(
      final Set<Map.Entry<UUID, FileProvenience>> entries, final ProviderHook hook) {
    return Observable.merge(
        entries
            .stream()
            .map(
                entry ->
                    hook.getRepository()
                        .save(new FileProvenience(entry.getValue().getFile(), localRepository)))
            .collect(Collectors.toList()));
  }

  private Observable<Boolean> localChain(final Set<Map.Entry<UUID, FileProvenience>> maps) {
    return Observable.merge(
            maps.stream()
                .map(
                    entry -> {
                      if (entry.getValue().getOrigin().isLocal()) {
                        fileEvents.onNext(
                            new FileEvent(
                                entry.getValue().getFile(),
                                FileEvent.Type.synched,
                                FileEvent.Location.local));
                        return Observable.just(true);
                      } else {
                        if (Utils.trackedFile.test(entry.getValue().getFile())) {
                          localConfig
                              .getLocalFiles()
                              .stream()
                              .filter(localFile -> entry.getValue().getFile().equals(localFile))
                              .findFirst()
                              .ifPresent(
                                  localFile -> {
                                    VClock.Comparison comparison =
                                        localFile.compare(entry.getValue().getFile());
                                    fileEvents.onNext(
                                        new FileEvent(
                                            localFile,
                                            comparison == VClock.Comparison.older
                                                ? FileEvent.Type.unsynched
                                                : FileEvent.Type.synched,
                                            FileEvent.Location.local));
                                  });
                        }
                        return localRepository.save(entry.getValue());
                      }
                    })
                .collect(Collectors.toList()))
        .defaultIfEmpty(false)
        .last();
  }

  public Observable<Multimap<UUID, FileProvenience>> packFilesById() {
    List<Observable<Tuple2<ProviderHook, RemoteConfig>>> obconfigs =
        providers
            .enabledProviders()
            .stream()
            .map(
                provider ->
                    Observable.create(provider.getAcquirer())
                        .map(
                            releaser -> {
                              releaser.getReleaser().call();
                              return Tuples.of(provider, releaser.getRemoteConfig());
                            }))
            .collect(Collectors.toList());
    return Observable.zip(
        obconfigs,
        args -> {
          final Multimap<UUID, FileProvenience> all = LinkedHashMultimap.create();
          //add local files
          localConfig
              .getLocalFiles()
              .stream()
              .filter(Utils.trackedFile)
              .forEach(file -> all.put(file.getId(), new FileProvenience(file, localRepository)));
          //add all remote files
          Tuple2<ProviderHook, RemoteConfig>[] configs =
              Arrays.copyOf(args, args.length, Tuple2[].class);
          Stream.of(configs)
              .forEach(
                  config ->
                      config
                          .getT2()
                          .getRemoteFiles()
                          .forEach(
                              file ->
                                  all.put(
                                      file.getId(),
                                      new FileProvenience(file, config.getT1().getRepository()))));
          return all;
        });
  }
}
