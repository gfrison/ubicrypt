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
package ubicrypt.core.remote;

import org.slf4j.Logger;

import java.util.Optional;
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
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.UbiProvider;

import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.slf4j.LoggerFactory.getLogger;

public class OnUpdateRemote extends RemoteAction {
  private static final Logger log = getLogger(OnUpdateRemote.class);

  public OnUpdateRemote(UbiProvider provider, IRepository repository) {
    super(provider, repository);
  }

  @Override
  public boolean test(FileProvenience fileProvenience, RemoteConfig remoteConfig) {
    UbiFile file = fileProvenience.getFile();
    Optional<RemoteFile> rfile =
      remoteConfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst();
    if (!rfile.isPresent()) {
      return false;
    }
    log.trace(
      "path:{}, local v:{}, remote v:{}, comparison:{}, test:{}",
      file.getPath(),
      file.getVclock(),
      rfile.get().getVclock(),
      file.compare(rfile.get()),
      file.compare(rfile.get()) == VClock.Comparison.newer);
    return file.compare(rfile.get()) == VClock.Comparison.newer;
  }

  @Override
  public Observable<Boolean> apply(final FileProvenience fp, final RemoteConfig rconfig) {
    UbiFile file = fp.getFile();
    RemoteFile rfile =
      rconfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst().get();

    log.debug("override file:{} on provider:{}", file.getPath(), provider);
    final AtomicReference<FileEvent.Type> fileEventType = new AtomicReference<>();
    if (!Utils.trackedFile.test(file)) {
      //delete remotely
      if (file.isRemoved()) {
        fileEventType.set(FileEvent.Type.removed);
      }
      if (file.isDeleted()) {
        fileEventType.set(FileEvent.Type.deleted);
      }
      return provider
        .delete(rfile.getName())
        .doOnNext(
          saved -> log.info("deleted:{} file:{}, in:{}", saved, rfile.getPath(), provider))
        .doOnNext(
          saved -> {
            if (saved) {
              rfile.copyFrom(file);
            }
          })
        .doOnError(err -> rfile.setError(true))
        .doOnCompleted(fileEvents(fp, fileEventType.get()));
    }
    //update remotely
    fileEventType.set(FileEvent.Type.updated);
    return fp.getOrigin()
      .get(file)
      .flatMap(
        is -> {
          //renew encryption key
          final Key key = new Key(AESGCM.rndKey(), UbiFile.KeyType.aes);
          return provider
            .put(
              rfile.getName(),
              AESGCM.encryptIs(
                key.getBytes(),
                new DeflaterInputStream(monitor(fp, is), new Deflater(BEST_COMPRESSION))))
            .doOnNext(
              saved ->
                log.info("updated:{} file:{}, in:{}", saved, rfile.getPath(), provider))
            .doOnNext(
              saved -> {
                rfile.copyFrom(file);
                rfile.setKey(key);
                rfile.setError(false);
              })
            .doOnError(err -> rfile.setError(true))
            .doOnCompleted(fileEvents(fp, fileEventType.get()));
        });
  }
}
