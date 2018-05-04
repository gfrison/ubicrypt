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

import com.google.common.collect.ForwardingSet;

import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.Action.add;
import static ubicrypt.core.Action.delete;
import static ubicrypt.core.Action.unchanged;
import static ubicrypt.core.Action.update;

public class RemoteFilesDelegate extends ForwardingSet<RemoteFile>
    implements IListener<RemoteFile> {
  private static final Logger log = getLogger(RemoteFilesDelegate.class);
  private final Set<RemoteFile> delegate;
  private final int maxFilesPerIndex;
  private final FileIndex index;

  public RemoteFilesDelegate(FileIndex index, int maxFilesPerIndex) {
    this.index = deepClone(index);
    //    index.getFiles().stream()
    //        .forEach(rf -> rf.registerUpdateListener(this));
    this.delegate =
        StreamSupport.stream(index.spliterator(), false)
            .map(FileIndex::getFiles)
            .flatMap(Set::stream)
            .collect(LinkedHashSet::new, (set, file) -> set.add(file), (s1, s2) -> s1.addAll(s2));
    delegate.forEach(rf -> rf.registerUpdateListener(this));
    this.maxFilesPerIndex = maxFilesPerIndex;
  }

  private static FileIndex deepClone(FileIndex idx) {
    FileIndex nidx = new FileIndex();
    nidx.setStatus(idx.getStatus());
    nidx.setFiles(new LinkedHashSet<>(idx.getFiles()));
    nidx.setNextIndex(idx.getNextIndex());
    if (idx.getNext() != null) {
      nidx.setNext(deepClone(idx.getNext()));
      nidx.getNext().setParent(nidx);
    }
    return nidx;
  }

  @Override
  protected Set<RemoteFile> delegate() {
    return delegate;
  }

  @Override
  public boolean add(RemoteFile element) {
    List<FileIndex> indexes = indexes();
    Optional<FileIndex> idx =
        indexes.stream().filter(fi -> fi.getFiles().size() < maxFilesPerIndex).findFirst();
    if (idx.isPresent()) {
      final FileIndex fileIndex = idx.get();
      fileIndex.getFiles().add(element);
      if (fileIndex.getStatus() == unchanged) {
        fileIndex.setStatus(update);
      }
    } else {
      FileIndex last = indexes.get(indexes.size() - 1);
      if (last.getStatus() == unchanged) {
        last.setStatus(update);
      }
      final FileIndex nfi = new FileIndex();
      last.setNext(nfi);
      nfi.setParent(last);
      nfi.setStatus(add);
      nfi.getFiles().add(element);
    }
    return delegate.add(element);
  }

  @Override
  public boolean addAll(Collection<? extends RemoteFile> collection) {
    collection.forEach(this::add);
    return true;
  }

  @Override
  public boolean remove(Object object) {
    List<FileIndex> indexes = indexes();
    Optional<FileIndex> idx =
        indexes.stream().filter(fi -> fi.getFiles().contains(object)).findFirst();
    if (!idx.isPresent()) {
      log.warn("no index present for remote file:{}", object);
      return false;
    }
    final FileIndex fileIndex = idx.get();
    fileIndex.getFiles().remove(object);
    if (fileIndex.getFiles().isEmpty()) {
      if (fileIndex.getParent() == null) {
        if (fileIndex.getStatus() == unchanged) {
          fileIndex.setStatus(update);
        }
      } else {
        fileIndex.setStatus(delete);
        fileIndex.getParent().setStatus(update);
      }
    }
    return delegate.remove(object);
  }

  private List<FileIndex> indexes() {
    return StreamSupport.stream(index.spliterator(), false).collect(Collectors.toList());
  }

  @Override
  public void onChange(RemoteFile obj) {
    List<FileIndex> indexes = indexes();
    Optional<FileIndex> idx =
        indexes.stream().filter(fi -> fi.getFiles().contains(obj)).findFirst();
    if (!idx.isPresent()) {
      log.warn("no index present for remote file:{}", obj);
      return;
    }
    final FileIndex fileIndex = idx.get();
    if (fileIndex.getStatus() == unchanged) {
      fileIndex.setStatus(update);
    }
  }

  public FileIndex getIndex() {
    return index;
  }
}
