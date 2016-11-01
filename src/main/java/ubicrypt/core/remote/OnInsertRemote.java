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

import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;

import rx.Observable;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.UbiProvider;

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.just;

public class OnInsertRemote extends RemoteAction {
    private static final Logger log = getLogger(OnInsertRemote.class);

    public OnInsertRemote(UbiProvider provider, IRepository repository) {
        super(provider, repository);
    }

    @Override
    public boolean test(FileProvenience fileProvenience, RemoteConfig remoteConfig) {
        UbiFile file = fileProvenience.getFile();
        return !remoteConfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst().isPresent();
    }

    @Override
    public Observable<Boolean> apply(FileProvenience fp, RemoteConfig rconfig) {
        UbiFile<UbiFile> file = fp.getFile();
        final AtomicReference<FileEvent.Type> fileEventType = new AtomicReference<>();

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
                                    rconfig.getRemoteFiles().add(rf);
                                    return true;
                                }))
                .defaultIfEmpty(false)
                .doOnCompleted(fileEvents(fp, fileEventType.get()));
    }


}
