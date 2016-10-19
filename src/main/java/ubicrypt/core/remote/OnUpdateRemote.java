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

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;

import javax.annotation.Resource;

import rx.Observable;
import rx.functions.Action0;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.MonitorInputStream;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.UbiProvider;

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.slf4j.LoggerFactory.getLogger;

public class OnUpdateRemote implements BiFunction<FileProvenience, RemoteFile, Observable<Boolean>> {
    private static final Logger log = getLogger(OnUpdateRemote.class);
    private final UbiProvider provider;
    private final IRepository repository;
    @Resource
    @Qualifier("progressEvents")
    private PublishSubject<ProgressFile> progressEvents;
    @Resource
    @Qualifier("fileEvents")
    private Subject<FileEvent, FileEvent> fileEvents;

    public OnUpdateRemote(UbiProvider provider, IRepository repository) {
        this.provider = provider;
        this.repository = repository;
    }


    @Override
    public Observable<Boolean> apply(FileProvenience fp, RemoteFile rfile) {
        UbiFile file = fp.getFile();
        log.debug("file:{} newer than:{} on provider:{}", file.getPath(), rfile, provider);
        final AtomicReference<FileEvent.Type> fileEventType = new AtomicReference<>();
        if (!Utils.trackedFile.test(file)) {
            //delete remotely
            if (file.isRemoved()) {
                fileEventType.set(FileEvent.Type.removed);
            }
            if (file.isDeleted()) {
                fileEventType.set(FileEvent.Type.deleted);
            }
            return provider.delete(rfile.getName())
                    .doOnNext(saved -> log.info("deleted:{} file:{}, to provider:{}", saved, rfile.getPath(), provider))
                    .doOnNext(saved -> {
                        if (saved) {
                            rfile.copyFrom(file);
                        }
                    })
                    .doOnError(err -> rfile.setError(true))
                    .doOnCompleted(fileEvents(fp, fileEventType.get()));
        }
        //update remotely
        fileEventType.set(FileEvent.Type.updated);
        return fp.getOrigin().get(file)
                .flatMap(is -> {
                    //renew encryption key
                    final Key key = new Key(AESGCM.rndKey(), UbiFile.KeyType.aes);
                    return provider.put(rfile.getName(),
                            AESGCM.encryptIs(key.getBytes(), new DeflaterInputStream(monitor(fp, is), new Deflater(BEST_COMPRESSION))))
                            .doOnNext(saved -> log.info("updated:{} file:{}, to provider:{}", saved, rfile.getPath(), provider))
                            .doOnNext(saved -> {
                                rfile.copyFrom(file);
                                rfile.setKey(key);
                            })
                            .doOnError(err -> rfile.setError(true))
                            .doOnCompleted(fileEvents(fp, fileEventType.get()));
                });
    }

    private Action0 fileEvents(final FileProvenience fp, final FileEvent.Type fileEventType) {
        return () -> fileEvents.onNext(new FileEvent(fp.getFile(), fileEventType, FileEvent.Location.remote));
    }

    private InputStream monitor(final FileProvenience fp, final InputStream inputStream) {
        final MonitorInputStream mis = new MonitorInputStream(inputStream);
        mis.monitor().subscribe(chunk -> progressEvents.onNext(new ProgressFile(fp, repository, chunk)),
                err -> {
                    log.error(err.getMessage(), err);
                    progressEvents.onNext(new ProgressFile(fp, repository, false, true));
                },
                () -> {
                    log.debug("send complete progress file:{}", fp.getFile());
                    progressEvents.onNext(new ProgressFile(fp, repository, true, false));
                });
        return mis;
    }

    public void setProgressEvents(PublishSubject<ProgressFile> progressEvents) {
        this.progressEvents = progressEvents;
    }

    public void setFileEvents(Subject<FileEvent, FileEvent> fileEvents) {
        this.fileEvents = fileEvents;
    }
}
