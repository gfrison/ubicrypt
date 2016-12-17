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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.exp.AlreadyManagedException;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.provider.ProviderLifeCycle;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

public class FileCommander implements IFileCommander {
  private static final Logger log = LoggerFactory.getLogger(FileCommander.class);
  private final Path basePath;

  @Resource
  @Qualifier("fileEvents")
  Subject<FileEvent, FileEvent> fileEvents = PublishSubject.create();

  @Inject private ProviderLifeCycle providerLifeCycle;
  @Inject private LocalConfig localConfig;
  @Inject private LocalRepository localRepository;
  @Inject private int deviceId;

  public FileCommander(final Path basePath) {
    this.basePath = basePath;
  }

  @Override
  public Observable<Tuple2<LocalFile, Observable<Boolean>>> addFile(final Path absolutePath) {
    return Observable.create(
            (Observable.OnSubscribe<Tuple2<LocalFile, Observable<Boolean>>>)
                subscriber -> {
                  if (absolutePath == null) {
                    subscriber.onError(
                        new IllegalArgumentException("absolutePath must not be null"));
                    return;
                  }
                  try {
                    Path relPath = basePath.relativize(absolutePath);
                    if (findLocalFile(relPath, localConfig).isPresent()) {
                      log.info("file {} already managed", relPath);
                      subscriber.onError(new AlreadyManagedException(absolutePath));
                      return;
                    }
                    final BasicFileAttributes attrs =
                        Files.readAttributes(absolutePath, BasicFileAttributes.class);
                    LocalFile lfile = new LocalFile();
                    lfile.setLastModified(attrs.lastModifiedTime().toInstant());
                    lfile.setVclock(new VClock());
                    lfile.setDeleted(false);
                    lfile.setPath(relPath);
                    lfile.setActive(true);
                    lfile.setRemoved(false);
                    lfile.setSize(attrs.size());
                    lfile.getVclock().increment(deviceId);
                    localConfig.getLocalFiles().add(lfile);
                    subscriber.onNext(
                        Tuple.of(
                            lfile,
                            Observable.merge(
                                    providerLifeCycle
                                        .enabledProviders()
                                        .stream()
                                        .map(
                                            hook ->
                                                hook.getRepository()
                                                    .save(
                                                        new FileProvenience(
                                                            lfile, localRepository)))
                                        .collect(Collectors.toList()))
                                .defaultIfEmpty(false)
                                .last()));
                    subscriber.onCompleted();
                  } catch (Exception e) {
                    subscriber.onError(e);
                  }
                })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Observable<Boolean> removeFile(final Path absolutePath) {
    checkNotNull(absolutePath);
    log.info("removing file:{}", absolutePath);
    return update(absolutePath, file -> file.setRemoved(true));
  }

  @Override
  public Observable<Boolean> deleteFile(final Path path) {
    log.info("deleting file:{}", path);
    findLocalFile(basePath.relativize(path), localConfig)
        .ifPresent(
            localFile ->
                fileEvents.onNext(
                    new FileEvent(localFile, FileEvent.Type.deleted, FileEvent.Location.local)));
    return update(path, file -> file.setDeleted(true));
  }

  Observable<Boolean> update(final Path absolutePath, final Consumer<LocalFile> localFileConsumer) {
    return Observable.create(
        subscriber -> {
          if (absolutePath == null) {
            subscriber.onError(new IllegalArgumentException("absolutePath must not be null"));
            return;
          }
          final Path relPath = basePath.relativize(absolutePath);
          final Optional<LocalFile> localFile = findLocalFile(relPath, localConfig);
          if (!localFile.isPresent()) {
            log.info("path {} not managed", relPath);
            subscriber.onError(new RuntimeException(format("path %s not managed", relPath)));
          }
          localFileConsumer.accept(localFile.get());
          localFile.get().getVclock().increment(deviceId);
          log.debug(
              "submit update active providers num:{}", providerLifeCycle.enabledProviders().size());
          final List<Observable<Boolean>> jobs =
              providerLifeCycle
                  .enabledProviders()
                  .stream()
                  .map(
                      hook ->
                          hook.getRepository()
                              .save(new FileProvenience(localFile.get(), localRepository)))
                  .collect(Collectors.toList());
          Observable.merge(jobs)
              .doOnSubscribe(() -> log.debug("update subscribed"))
              .subscribe(subscriber);
        });
  }

  private Optional<LocalFile> findLocalFile(final Path path, final LocalConfig localConfig) {
    return localConfig
        .getLocalFiles()
        .stream()
        .filter(file -> file.getPath().equals(path))
        .findFirst();
  }

  @Override
  public Observable<Boolean> updateFile(final Path absolutePath) {
    return Observable.create(
        subscriber -> {
          if (absolutePath == null) {
            subscriber.onError(new IllegalArgumentException("path must not be null"));
            return;
          }
          final Path relPath = basePath.relativize(absolutePath);
          log.info("updating file:{}", relPath);
          final Optional<LocalFile> localFile = findLocalFile(relPath, localConfig);
          if (!localFile.isPresent()) {
            log.info("path {} not managed", relPath);
            subscriber.onError(new RuntimeException(format("path %s not managed", absolutePath)));
            return;
          }
          final LocalFile file = localFile.get();
          final BasicFileAttributes attrs;
          try {
            attrs = Files.readAttributes(absolutePath, BasicFileAttributes.class);
          } catch (IOException e) {
            subscriber.onError(e);
            return;
          }
          if (file.getLastModified().isAfter(attrs.lastModifiedTime().toInstant())) {
            subscriber.onError(
                new IllegalArgumentException(
                    format(
                        "can't update %s, last update:%s, current:%s",
                        relPath, file.getLastModified(), attrs.lastModifiedTime().toInstant())));
            return;
          }
          file.setLastModified(attrs.lastModifiedTime().toInstant());
          file.setSize(attrs.size());
          file.getVclock().increment(deviceId);

          Observable.merge(
                  providerLifeCycle
                      .enabledProviders()
                      .stream()
                      .map(
                          hook ->
                              hook.getRepository().save(new FileProvenience(file, localRepository)))
                      .collect(Collectors.toList()))
              .subscribe(subscriber);
        });
  }
}
