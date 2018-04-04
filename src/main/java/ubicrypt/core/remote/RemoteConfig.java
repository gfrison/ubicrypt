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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.fdx.IndexRecord;
import ubicrypt.core.fdx.RemoteFileAction;
import ubicrypt.core.provider.UbiProvider;

import static org.slf4j.LoggerFactory.getLogger;

public class RemoteConfig {
  private static final Logger log = getLogger(RemoteConfig.class);
  private final Set<UbiProvider> providers;
  private final Set<IndexRecord> records;
  private final int maxFilesPerIndex;
  private final List<FileIndexAction> changedIndexes = new ArrayList<>();

  public RemoteConfig(Set<UbiProvider> providers, Set<IndexRecord> records, int maxFilesPerIndex) {
    this.providers = providers;
    this.records = records;
    this.maxFilesPerIndex = maxFilesPerIndex;
  }

  public Set<UbiProvider> getProviders() {
    return providers;
  }


  public synchronized Set<RemoteFile> getRemoteFiles() {
    Set<RemoteFile> delegate = records.stream().map(IndexRecord::getRemoteFile).collect(Collectors.toSet());
    return new ForwardingSet<>() {
      @Override
      protected Set<RemoteFile> delegate() {
        return delegate;
      }

      @Override
      public boolean add(RemoteFile element) {
        List<IndexRecord> indexes = fileIndices();
        Optional<IndexRecord> idx = indexes.stream()
            .filter(fi -> fi.getFileIndex().getFiles().size() < maxFilesPerIndex)
            .findFirst();
        if (idx.isPresent()) {
          idx.get().getFileIndex().getFiles().add(element);
          changedIndexes.add(new FileIndexAction(Action.update, idx.get().getRemoteFile().getRemoteName(), idx.get().getFileIndex()));
        } else {
          final IndexRecord last = indexes.get(indexes.size() - 1);
          final FileIndex nfi = new FileIndex();
          nfi.getFiles().add(element);
          changedIndexes.add(new FileIndexAction(Action.update, last.getRemoteFile().getRemoteName(), last.getFileIndex()));
          changedIndexes.add(new FileIndexAction(Action.add, last, nfi));
        }
        delegate.add(element);
        return records.add(new IndexRecord(idx.get(), element, IndexRecord.IRStatus.created));
      }

      @Override
      public boolean remove(Object object) {
        List<IndexRecord> indexes = fileIndices();
        Optional<FileIndex> idx = indexes.stream()
            .filter(fi -> fi.getFileIndex().getFiles().contains(object))
            .map(IndexRecord::getFileIndex)
            .findFirst();
        if (!idx.isPresent()) {
          log.warn("no index present for remote file:{}", object);
          return false;
        }
        idx.get().getFiles().remove(object);
        if (idx.get().getFiles().isEmpty()) {
          changedIndexes.add(new FileIndexAction(Action.delete, ))
        }
        delegate.remove(object);

        return super.remove(object);
      }
    };
  }

  private List<IndexRecord> fileIndices() {
    return records.stream()
        .distinct()
        .sorted((f1, f2) -> (f1.getFileIndex().getNextIndex() == null) ? 1 : (f2.getFileIndex().getNextIndex() == null) ? -1 : 0)
        .collect(Collectors.toList());
  }


}
