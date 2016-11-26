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
package ubicrypt.core.local;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.util.CopyFile;
import ubicrypt.core.util.StoreTempFile;
import ubicrypt.core.util.SupplierExp;

import static org.slf4j.LoggerFactory.getLogger;

public class OnNewLocal implements Func1<FileProvenience, Observable<Boolean>> {
    private static final Logger log = getLogger(OnNewLocal.class);
    @Inject
    private Path basePath;
    @Inject
    private LocalConfig localConfig;
    @Resource
    @Qualifier("fileEvents")
    private Subject<FileEvent, FileEvent> fileEvents;

    @Override
    public Observable<Boolean> call(FileProvenience fp) {
        if (!Utils.trackedFile.test(fp.getFile())) {
            return Observable.just(false);
        }
        UbiFile<UbiFile> rfile = fp.getFile();
        final Path path = basePath.resolve(rfile.getPath());
        log.info("copy to:{} from repo:{}", path, fp.getOrigin());
        final LocalFile lc = new LocalFile();
        lc.copyFrom(rfile);
        if (Files.exists(path)) {
            final BasicFileAttributes attrs = SupplierExp.silent(() -> Files.readAttributes(path, BasicFileAttributes.class));
            if (attrs.isDirectory()) {
                log.info("can't import file, a folder already exists with the same name:{}", path);
                return Observable.just(false);
            }
            if (attrs.size() == rfile.getSize()) {
                log.info("identical file already present locally:{}", path);
                localConfig.getLocalFiles().add(LocalFile.copy(rfile));
                return Observable.just(true).doOnCompleted(() -> fileEvents.onNext(new FileEvent(rfile, FileEvent.Type.created, FileEvent.Location.local)));
            }
            log.info("conflicting file already present locally:{}", path);
//            conflictEvents.onNext(rfile);
            return Observable.just(false);
        }
        AtomicReference<Path> tempFile = new AtomicReference<>();
        return fp.getOrigin().get(rfile)
                .flatMap(new StoreTempFile())
                .doOnNext(tempFile::set)
                .map(new CopyFile(rfile.getSize(), path, true, fp.getFile().getLastModified()))
                .doOnCompleted(() -> {
                    if (tempFile.get() != null) {
                        try {
                            Files.delete(tempFile.get());
                        } catch (IOException e) {
                        }
                    }
                })
                .onErrorReturn(err -> {
                    if (err instanceof NotFoundException) {
                        log.warn("remote file:{} not found in:{}", fp.getFile().getPath(), fp.getOrigin());
                    }
                    fp.getOrigin().error(fp.getFile());
                    return false;
                })
                .subscribeOn(Schedulers.io())
                .doOnNext(next -> {
                    if (next) {
                        localConfig.getLocalFiles().add(LocalFile.copy(rfile));
                    }
                })
                .doOnNext(next -> fileEvents.onNext(new FileEvent(rfile, next ? FileEvent.Type.created : FileEvent.Type.error, FileEvent.Location.local)));
    }

    public void setBasePath(Path basePath) {
        this.basePath = basePath;
    }

    public void setLocalConfig(LocalConfig localConfig) {
        this.localConfig = localConfig;
    }

    public void setFileEvents(Subject<FileEvent, FileEvent> fileEvents) {
        this.fileEvents = fileEvents;
    }
}
