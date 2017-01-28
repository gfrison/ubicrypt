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
package ubicrypt.core.fdx;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import rx.functions.Func1;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;

import static ubicrypt.core.fdx.IndexRecord.IRStatus.created;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.deleted;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.modified;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.unchanged;

public class RemoteFileAction2Record implements Func1<List<RemoteFileAction>, List<IndexRecord>> {
  private final RemoteFile firstIndex;
  private final List<FileIndex> indexes;
  private final int nmaxItemsPerFile;

  public RemoteFileAction2Record(RemoteFile first, List<FileIndex> indexes, int nmaxItemsPerFile) {
    this.firstIndex = first;
    this.indexes = indexes;
    this.nmaxItemsPerFile = nmaxItemsPerFile;
  }

  @Override
  public List<IndexRecord> call(List<RemoteFileAction> fileChanged) {
    final RemoteFile[] rf = {firstIndex};
    ArrayList<IndexRecord> records = new ArrayList<>();
    indexes.forEach(
        fi -> {
          records.add(new IndexRecord(fi, rf[0]));
          rf[0] = fi.getNextIndex();
        });
    //find the fi element
    fileChanged.forEach(
        changed -> {
          switch (changed.getAction()) {
            case add:
              //add new file
              //find a place to add the file
              Optional<IndexRecord> irHoleOpt =
                  records
                      .stream()
                      .filter(ir -> ir.getFileIndex().getFiles().size() < nmaxItemsPerFile)
                      .findFirst();
              if (irHoleOpt.isPresent()) {
                IndexRecord ir = irHoleOpt.get();
                if (ir.getStatus() == unchanged) {
                  ir.setStatus(modified);
                }
                ir.getFileIndex().getFiles().add(changed.getRemoteFile());
              } else {
                //create new fileindex
                final FileIndex fileIndex = new FileIndex();
                fileIndex.getFiles().add(changed.getRemoteFile());
                if (!records.isEmpty()) {
                  IndexRecord last = records.get(records.size() - 1);
                  if (last.getStatus() == unchanged) {
                    last.setStatus(modified);
                  }
                }
                RemoteFile remoteFile = new RemoteFile();
                if (records.isEmpty()) {
                  remoteFile = firstIndex;
                }
                records.add(new IndexRecord(fileIndex, remoteFile, created));
              }
              break;
            case update:
              final IndexRecord ir =
                  records
                      .stream()
                      .filter(
                          ir1 -> ir1.getFileIndex().getFiles().contains(changed.getRemoteFile()))
                      .findFirst()
                      .get();
              ir.setStatus(modified);
              ir.getFileIndex().getFiles().add(changed.getRemoteFile());
              break;
            case delete:
              final IndexRecord irr =
                  records
                      .stream()
                      .filter(
                          ir1 -> ir1.getFileIndex().getFiles().contains(changed.getRemoteFile()))
                      .findFirst()
                      .get();
              final Set<RemoteFile> files = irr.getFileIndex().getFiles();
              files.remove(changed.getRemoteFile());
              if (files.isEmpty()) {
                int pos = records.indexOf(irr);
                if (pos > 0) {
                  //not the first element
                  IndexRecord previous = records.get(pos - 1);
                  if (previous.getStatus() == unchanged) {
                    previous.setStatus(modified);
                  }
                }
                irr.setStatus(deleted);
              } else {
                irr.setStatus(modified);
              }
              break;
          }
        });
    return records;
  }
}
