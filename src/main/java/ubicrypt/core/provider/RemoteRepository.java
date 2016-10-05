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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Actions;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.MonitorInputStream;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.util.QueueLiner;

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.create;
import static rx.Observable.just;

public class RemoteRepository implements IRepository {
    private static final Logger log = getLogger(RemoteRepository.class);
    final AtomicReference<AcquirerReleaser> releaserRef = new AtomicReference<>();
    private final Observable.OnSubscribe<AcquirerReleaser> acquirer;
    private final RemoteIO<RemoteConfig> configIO;
    private final UbiProvider provider;
    @Resource
    private PublishSubject<ProgressFile> progressEvents = PublishSubject.create();
    @Resource
    private Subject<FileEvent, FileEvent> fileEvents = PublishSubject.create();
    @Resource
    private QueueLiner<Boolean> queueLiner;
    private Func1<Observable<Boolean>, Observable<Boolean>> epilogued;
    private RemoteFileGetter fileGetter;


    public RemoteRepository(final Observable.OnSubscribe<AcquirerReleaser> acquirer, final UbiProvider provider, final RemoteIO<RemoteConfig> configIO) {
        this.acquirer = acquirer;
        this.provider = provider;
        this.configIO = configIO;
        fileGetter = new RemoteFileGetter(acquirer, provider);
        this.epilogued = (booleanObservable -> booleanObservable);
    }


    @PostConstruct
    public void init() {
        this.epilogued = queueLiner.createEpiloguer(() -> saveConf(releaserRef.get().getRemoteConfig()));
    }

    @Override
    public Observable<InputStream> get(final UbiFile file) {
        return fileGetter.call(file, (rfile, is) -> monitor(new FileProvenience(file, this), new InflaterInputStream(AESGCM.decryptIs(rfile.getKey().getBytes(), is))));
    }

    private Observable<Boolean> saveConf(final RemoteConfig remoteConfig) {
        AtomicReference<Action0> releaser = new AtomicReference<>();
        return create(acquirer)
                .doOnNext(acquirerReleaser -> releaser.set(acquirerReleaser.getReleaser()))
                .flatMap(rel -> configIO.apply(remoteConfig)
                        .doOnError(err -> releaser.get().call())
                        .doOnCompleted(releaser.get()));
    }


    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public Observable<Boolean> save(final FileProvenience fp) {
        //only one remote save at time
        return epilogued.call(saveSerial(fp));
    }

    private Observable<Boolean> saveSerial(final FileProvenience fp) {
        //acquire permission
        final AtomicReference<FileEvent.Type> fileEventType = new AtomicReference<>();
        return create(acquirer).flatMap(releaser -> {
            releaserRef.set(releaser);
            final RemoteConfig remoteConfig = releaser.getRemoteConfig();
            UbiFile<UbiFile> file = fp.getFile();
            Optional<RemoteFile> rfile = remoteConfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst();
            if (!rfile.isPresent()) {
                if (!Utils.trackedFile.test(file)) {
                    return just(false);
                }
                //create new one remotely
                RemoteFile rf = RemoteFile.createFrom(file);
                final byte[] key = AESGCM.rndKey();
                rf.setKey(new Key(key));
                fileEventType.set(FileEvent.Type.created);
                return fp.getOrigin().get(file)
                        .flatMap(is ->
                                provider.post(AESGCM.encryptIs(key, new DeflaterInputStream(monitor(fp, is), new Deflater(BEST_COMPRESSION))))
                                        .map(name -> {
                                            log.info("created file:{}, to provider:{}", rf.getPath(), provider);
                                            //add name and add to config
                                            rf.setRemoteName(name);
                                            remoteConfig.getRemoteFiles().add(rf);
                                            return true;
                                        }))
                        .defaultIfEmpty(false)
//                        .filter(BooleanUtils::isTrue)
                        .doOnCompleted(fileEvents(fp, fileEventType.get()));
            }
            //update already present file
            if (file.compare(rfile.get()) == VClock.Comparison.newer) {
                //coming file is new version
                log.debug("file:{} newer than:{} on provider:{}", file.getPath(), rfile.get(), provider);
                if (!Utils.trackedFile.test(file)) {
                    //delete remotely
                    if (file.isRemoved()) {
                        fileEventType.set(FileEvent.Type.removed);
                    }
                    if (file.isDeleted()) {
                        fileEventType.set(FileEvent.Type.deleted);
                    }
                    return provider.delete(rfile.get().getName())
                            .doOnNext(saved -> log.info("deleted:{} file:{}, to provider:{}", saved, rfile.get().getPath(), provider))
                            .doOnNext(saved -> {
                                if (saved) {
                                    rfile.get().copyFrom(file);
                                }
                            })
                            .doOnError(err -> rfile.get().setError(true))
//                            .filter(BooleanUtils::isTrue)
                            .doOnCompleted(fileEvents(fp, fileEventType.get()));
                }
                //update remotely
                fileEventType.set(FileEvent.Type.updated);
                return fp.getOrigin().get(file)
                        .flatMap(is -> {
                            //renew encryption key
                            final Key key = new Key(AESGCM.rndKey(), UbiFile.KeyType.aes);
                            return provider.put(rfile.get().getName(),
                                    AESGCM.encryptIs(key.getBytes(), new DeflaterInputStream(monitor(fp, is), new Deflater(BEST_COMPRESSION))))
                                    .doOnNext(saved -> log.info("updated:{} file:{}, to provider:{}", saved, rfile.get().getPath(), provider))
                                    .doOnNext(saved -> {
                                        rfile.get().copyFrom(file);
                                        rfile.get().setKey(key);
                                    })
                                    .doOnError(err -> rfile.get().setError(true))
//                                    .filter(BooleanUtils::isTrue)
                                    .doOnCompleted(fileEvents(fp, fileEventType.get()));
                        });
            }
            log.trace("no update file:{} for provider:{}", file.getPath(), provider);
            return Observable.just(false);
        })
                .doOnError(releaserRef.get() != null ? err -> releaserRef.get().getReleaser().call() : Actions.empty())
                .doOnError(err -> progressEvents.onNext(new ProgressFile(fp, this, false, true)))
                .doOnError(err -> fileEvents(fp, FileEvent.Type.error))
                .onErrorReturn(err -> {
                    log.error(err.getMessage(), err);
                    return false;
                })
                .doOnCompleted(releaserRef.get() != null ? releaserRef.get().getReleaser()::call : Actions.empty());
    }

    private Action0 fileEvents(final FileProvenience fp, final FileEvent.Type fileEventType) {
        return () -> fileEvents.onNext(new FileEvent(fp.getFile(), fileEventType, FileEvent.Location.remote));
    }

    private InputStream monitor(final FileProvenience fp, final InputStream inputStream) {
        final MonitorInputStream mis = new MonitorInputStream(inputStream);
        mis.monitor().subscribe(chunk -> progressEvents.onNext(new ProgressFile(fp, this, chunk)),
                err -> log.error(err.getMessage(), err),
                () -> progressEvents.onNext(new ProgressFile(fp, this, true, false)));
        return mis;
    }


    public void setProgressEvents(final PublishSubject<ProgressFile> progressEvents) {
        this.progressEvents = progressEvents;
    }

    public void setFileEvents(final Subject<FileEvent, FileEvent> fileEvents) {
        this.fileEvents = fileEvents;
    }

    @Override
    public String toString() {
        return provider.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        final RemoteRepository that = (RemoteRepository) o;

        return new EqualsBuilder()
                .append(provider, that.provider)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(provider)
                .toHashCode();
    }


    public void setQueueLiner(final QueueLiner<Boolean> queueLiner) {
        this.queueLiner = queueLiner;
    }
}
