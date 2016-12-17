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
package ubicrypt.core.local;

import com.google.common.base.Throwables;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.functions.Func1;
import rx.subjects.Subject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.util.CopyFile;
import ubicrypt.core.util.StoreTempFile;

import static com.google.common.base.Preconditions.checkNotNull;

public class LocalRepository implements IRepository {

  private static final Logger log = LoggerFactory.getLogger(LocalRepository.class);
  private final Path basePath;

  @Resource
  @Qualifier("onNewLocal")
  Func1<FileProvenience, Observable<Boolean>> onNewFileLocal;

  @Inject private LocalConfig localConfig = new LocalConfig();

  @Resource
  @Qualifier("fileEvents")
  private Subject<FileEvent, FileEvent> fileEvents;

  @Resource
  @Qualifier("conflictEvents")
  private Subject<UbiFile, UbiFile> conflictEvents;

  public LocalRepository(final Path basePath) {
    this.basePath = basePath;
  }

  private static void check(final FileProvenience fp) {
    checkNotNull(fp, "FileProvenience must not be null");
    checkNotNull(fp.getFile(), "file must not be null");
    checkNotNull(fp.getOrigin(), "file must not be null");
  }

  @Override
  public Observable<Boolean> save(final FileProvenience fp) {
    try {
      check(fp);
      final UbiFile rfile = fp.getFile();
      final Optional<LocalFile> lfile =
          localConfig.getLocalFiles().stream().filter(lf -> lf.equals(rfile)).findFirst();
      if (!lfile.isPresent()) {
        //new file
        return onNewFileLocal.call(fp);
      } else {
        final VClock.Comparison comparison = rfile.getVclock().compare(lfile.get().getVclock());
        if (comparison == VClock.Comparison.newer) {
          lfile.get().copyFrom(rfile);
          if (!rfile.isDeleted() && !rfile.isRemoved()) {
            log.info("update file:{} locally from repo:{}", rfile.getPath(), fp.getOrigin());
            AtomicReference<Path> tempFile = new AtomicReference<>();
            return fp.getOrigin()
                .get(fp.getFile())
                .flatMap(new StoreTempFile())
                .map(
                    new CopyFile(
                        rfile.getSize(),
                        basePath.resolve(rfile.getPath()),
                        false,
                        fp.getFile().getLastModified()))
                .doOnCompleted(
                    () -> {
                      if (tempFile.get() != null) {
                        try {
                          Files.delete(tempFile.get());
                        } catch (IOException e) {
                        }
                      }
                    })
                .doOnCompleted(
                    () ->
                        fileEvents.onNext(
                            new FileEvent(
                                fp.getFile(), FileEvent.Type.updated, FileEvent.Location.local)));
          }
          //removed or deleted
          fileEvents.onNext(
              new FileEvent(
                  fp.getFile(),
                  rfile.isDeleted() ? FileEvent.Type.deleted : FileEvent.Type.removed,
                  FileEvent.Location.local));
        }
      }
      return Observable.just(false);
    } catch (Exception e) {
      return Observable.error(e);
    }
  }

  private Stream<LocalFile> getPathStream(final UbiFile file) {
    return localConfig.getLocalFiles().stream().filter(file1 -> file1.equals(file));
  }

  @Override
  public Observable<InputStream> get(final UbiFile file) {
    checkNotNull(file, "file must be not null");
    return Observable.create(
        subscriber -> {
          try {
            subscriber.onNext(
                getPathStream(file)
                    .map(LocalFile::getPath)
                    .map(basePath::resolve)
                    .map(Utils::readIs)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(basePath.resolve(file.getPath()))));
            subscriber.onCompleted();
          } catch (final Exception e) {
            subscriber.onError(e);
          }
        });
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  public LocalConfig getLocalConfig() {
    return localConfig;
  }

  public void setLocalConfig(LocalConfig localConfig) {
    this.localConfig = localConfig;
  }

  public BasicFileAttributes attributes(final Path path) {
    try {
      return Files.readAttributes(basePath.resolve(path), BasicFileAttributes.class);
    } catch (final IOException e) {
      Throwables.propagate(e);
    }
    return null;
  }

  public Path getBasePath() {
    return basePath;
  }

  public void setFileEvents(final Subject<FileEvent, FileEvent> fileEvents) {
    this.fileEvents = fileEvents;
  }

  public void setOnNewFileLocal(Func1<FileProvenience, Observable<Boolean>> onNewFileLocal) {
    this.onNewFileLocal = onNewFileLocal;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("basePath", basePath)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    final LocalRepository that = (LocalRepository) o;

    return new EqualsBuilder().append(basePath, that.basePath).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(basePath).toHashCode();
  }
}
