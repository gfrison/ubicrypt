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
package ubicrypt.core.remote;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import ubicrypt.core.IRepository;
import ubicrypt.core.MonitorInputStream;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.RemoteIO;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.RemoteFileGetter;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.provider.lock.AcquirerReleaser;
import ubicrypt.core.util.QueueLiner;

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
    private QueueLiner queueLiner;
    private Func1<Observable<Boolean>, Observable<Boolean>> outboundQueue;
    private Func1<Observable<InputStream>, Observable<InputStream>> inboundQueue;
    private RemoteFileGetter fileGetter;
    private List<IRemoteAction> actions;


    public RemoteRepository(final Observable.OnSubscribe<AcquirerReleaser> acquirer, final UbiProvider provider, final RemoteIO<RemoteConfig> configIO) {
        this.acquirer = acquirer;
        this.provider = provider;
        this.configIO = configIO;
        fileGetter = new RemoteFileGetter(acquirer, provider);
        this.outboundQueue = (booleanObservable -> booleanObservable);
    }


    @PostConstruct
    public void init() {
        this.outboundQueue = queueLiner.createEpiloguer(() -> saveConf(releaserRef.get().getRemoteConfig()));
        this.inboundQueue = queueLiner.createEpiloguer();
    }

    @Override
    public void error(UbiFile file) {
        AtomicReference<Action0> releaser = new AtomicReference<>();
        create(acquirer)
                .doOnNext(acquirerReleaser -> releaser.set(acquirerReleaser.getReleaser()))
                .map(AcquirerReleaser::getRemoteConfig)
                .flatMap(rc -> {
                    rc.getRemoteFiles().stream()
                            .filter(f -> f.equals(file))
                            .findFirst()
                            .ifPresent(rcf -> rcf.setError(true));
                    return saveConf(rc);
                })
                .doOnError(err -> {
                    if (releaser.get() != null) {
                        releaser.get().call();
                    }
                })
                .doOnCompleted(() -> releaser.get().call())
                .subscribe(Actions.empty(), Utils.logError);

    }

    @Override
    public Observable<InputStream> get(final UbiFile file) {
        return inboundQueue.call(fileGetter.call(file,
                (rfile, is) -> new MonitorInputStream(new InflaterInputStream(AESGCM.decryptIs(rfile.getKey().getBytes(), is))))
                .cast(MonitorInputStream.class)
                .flatMap(is -> create(subscriber -> {
                    final FileProvenience fp = new FileProvenience(file, this);
                    is.monitor().subscribe(chunk -> progressEvents.onNext(new ProgressFile(fp, chunk)),
                            err -> {
                                Utils.logError.call(err);
                                progressEvents.onNext(new ProgressFile(fp, false, true));
                                subscriber.onError(err);
                            }, () -> {
                                progressEvents.onNext(new ProgressFile(fp, true, false));
                                subscriber.onCompleted();
                            });
                    subscriber.onNext(is);
                })));
    }

    private Observable<Boolean> saveConf(final RemoteConfig remoteConfig) {
        AtomicReference<Action0> releaser = new AtomicReference<>();
        return create(acquirer)
                .doOnNext(acquirerReleaser -> releaser.set(acquirerReleaser.getReleaser()))
                .flatMap(rel -> configIO.apply(remoteConfig)
                        .doOnError(err -> releaser.get().call())
                        .doOnCompleted(() -> releaser.get().call()));
    }


    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public Observable<Boolean> save(final FileProvenience fp) {
        //only one remote save at time
        return outboundQueue.call(saveSerial(fp));
    }

    private Observable<Boolean> saveSerial(final FileProvenience fp) {
        //acquire permission
        return create(acquirer).flatMap(releaser -> {
            releaserRef.set(releaser);
            final RemoteConfig remoteConfig = releaser.getRemoteConfig();
            UbiFile<UbiFile> file = fp.getFile();
            return actions.stream()
                    .filter(test -> test.test(fp, remoteConfig))
                    .map(action -> action.apply(fp, remoteConfig))
                    .findFirst()
                    .orElseGet(() -> {
                        log.trace("no action for file:{} provider:{}", file.getPath(), provider);
                        return just(false);
                    });
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
                err -> {
                    log.error(err.getMessage(), err);
                    progressEvents.onNext(new ProgressFile(fp, this, false, true));
                },
                () -> {
                    log.debug("send complete progress file:{}", fp.getFile());
                    progressEvents.onNext(new ProgressFile(fp, this, true, false));
                });
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


    public void setQueueLiner(final QueueLiner queueLiner) {
        this.queueLiner = queueLiner;
    }

    public void setActions(List<IRemoteAction> actions) {
        this.actions = actions;
    }
}
