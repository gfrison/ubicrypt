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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;

import static java.lang.String.valueOf;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.dto.FileIndex.FileIndexBuilder.aFileIndex;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.created;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.modified;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.unchanged;
import static ubicrypt.core.Action.add;

public class RemoteFileAction2RecordTest {

  @Test
  public void addFiles() throws Exception {
    RemoteFile first = new RemoteFile();
    List<IndexRecord> res = add100Files(first);
    assertThat(res).hasSize(20);
    res.forEach(ir -> assertThat(ir.getFileIndex().getFiles()).hasSize(5));
  }

  public List<IndexRecord> add100Files(RemoteFile first) {
    List<FileIndex> indexes =
        range(0, 10)
            .mapToObj(
                i ->
                    aFileIndex()
                        .withFiles(
                            ImmutableSet.of(
                                new RemoteFile() {
                                  {
                                    setRemoteName(valueOf(i));
                                  }
                                }))
                        .withNextIndex(
                            new RemoteFile() {
                              {
                                setRemoteName(valueOf(i + 1));
                              }
                            })
                        .build())
            .collect(Collectors.toList());
    List<RemoteFileAction> changed =
        range(0, 100)
            .mapToObj(i -> new RemoteFileAction(add, new RemoteFile()))
            .collect(Collectors.toList());
    RemoteFileAction2Record rfa =
        new RemoteFileAction2Record(first, ImmutableList.of(new FileIndex()), 5);
    return rfa.call(changed);
  }

  @Test
  public void modify() throws Exception {
    RemoteFile first = new RemoteFile();
    List<FileIndex> indexes =
        add100Files(first).stream().map(IndexRecord::getFileIndex).collect(Collectors.toList());
    RemoteFileAction2Record rfs = new RemoteFileAction2Record(first, indexes, 5);
    final RemoteFileAction element =
        new RemoteFileAction(
            Action.update, indexes.get(0).getFiles().iterator().next());
    List<IndexRecord> res = rfs.call(ImmutableList.of(element));

    assertThat(res).hasSize(20);
    assertThat(res.get(0).getStatus()).isEqualTo(modified);
    range(1, 10).forEach(i -> assertThat(res.get(i).getStatus()).isEqualTo(unchanged));
  }

  @Test
  public void addNew() throws Exception {
    RemoteFile first = new RemoteFile();
    List<FileIndex> indexes =
        add100Files(first).stream().map(IndexRecord::getFileIndex).collect(Collectors.toList());
    RemoteFileAction2Record rfs = new RemoteFileAction2Record(first, indexes, 5);
    List<IndexRecord> res = rfs.call(ImmutableList.of());
    assertThat(res).hasSize(20);

    RemoteFileAction newfile = new RemoteFileAction(add, new RemoteFile());
    res = rfs.call(ImmutableList.of(newfile));
    assertThat(res).hasSize(21);
    final IndexRecord last = res.get(20);
    assertThat(last.getFileIndex().getFiles()).hasSize(1);
    assertThat(last.getStatus()).isEqualTo(created);
    final IndexRecord prelast = res.get(19);
    assertThat(prelast.getStatus()).isEqualTo(modified);
  }
}
