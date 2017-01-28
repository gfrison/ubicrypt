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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Persist;

import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static rx.Observable.create;
import static ubicrypt.core.TestUtils.createDirs;
import static ubicrypt.core.TestUtils.deleteDirs;
import static ubicrypt.core.TestUtils.fileProvider;
import static ubicrypt.core.TestUtils.tmp;
import static ubicrypt.core.fdx.RemoteFileAction.Action.add;
import static ubicrypt.core.fdx.RemoteFileAction.Action.delete;
import static ubicrypt.core.fdx.RemoteFileAction.Action.update;

public class FDXIT {
  private IPersist persist;

  @Before
  public void setUp() throws Exception {
    deleteDirs();
    createDirs();
    persist =
        new Persist(fileProvider(tmp)) {
          {
            setEncrypt(false);
          }
        };
  }

  @After
  public void tearDown() throws Exception {
    deleteDirs();
  }

  @Test
  public void addNew() throws Exception {
    List<RemoteFileAction> files = fileAction100();
    RemoteFile first = new RemoteFile();
    RemoteFileAction2Record converter = new RemoteFileAction2Record(first, ImmutableList.of(), 5);
    final List<IndexRecord> records = converter.call(files);
    FDXSaver saver = new FDXSaver(persist, records);
    //save
    assertThat(create(saver).toBlocking().last()).hasSize(20);

    FDXLoader loader = new FDXLoader(persist, first);
    List<FileIndex> indexes = create(loader).toList().toBlocking().last();
    assertThat(indexes).hasSize(20);
    //check tot num of files
    assertThat(
            indexes
                .stream()
                .map(FileIndex::getFiles)
                .map(Set::size)
                .mapToInt(Integer::intValue)
                .sum())
        .isEqualTo(100);
  }

  public List<RemoteFileAction> fileAction100() {
    return range(0, 100)
        .mapToObj(
            i ->
                new RemoteFile() {
                  {
                    setRemoteName(String.valueOf(i));
                  }
                })
        .map(rf -> new RemoteFileAction(add, rf))
        .collect(Collectors.toList());
  }

  @Test
  public void update() throws Exception {
    RemoteFile first = new RemoteFile();
    final List<IndexRecord> records = storeFiles(first);

    //modify 1 file
    final RemoteFile remoteFile = records.get(0).getFileIndex().getFiles().iterator().next();
    remoteFile.getVclock().increment(1);
    FDXSaver saver2 =
        new FDXSaver(
            persist,
            new RemoteFileAction2Record(
                    first,
                    records.stream().map(IndexRecord::getFileIndex).collect(Collectors.toList()),
                    5)
                .call(ImmutableList.of(new RemoteFileAction(update, remoteFile))));
    assertThat(create(saver2).toBlocking().last()).hasSize(20);

    //load all files
    FDXLoader loader = new FDXLoader(persist, first);
    List<FileIndex> loaded = create(loader).toList().toBlocking().last();
    assertThat(loaded).hasSize(20);
    VClock vclock = new VClock();
    vclock.increment(1);
    assertThat(loaded.get(0).getFiles().iterator().next().getVclock().compare(vclock))
        .isEqualTo(VClock.Comparison.equal);
  }

  public List<IndexRecord> storeFiles(RemoteFile first) {
    List<RemoteFileAction> files = fileAction100();
    RemoteFileAction2Record converter = new RemoteFileAction2Record(first, ImmutableList.of(), 5);
    FDXSaver saver = new FDXSaver(persist, converter.call(files));
    //save
    final List<IndexRecord> records = create(saver).toBlocking().last();
    assertThat(records).hasSize(20);
    return records;
  }

  @Test
  public void deleteAll() throws Exception {
    RemoteFile first = new RemoteFile();
    List<IndexRecord> records = storeFiles(first);
    FDXSaver saver2 =
        new FDXSaver(
            persist,
            new RemoteFileAction2Record(
                    first,
                    records.stream().map(IndexRecord::getFileIndex).collect(Collectors.toList()),
                    5)
                .call(
                    records
                        .stream()
                        .map(IndexRecord::getFileIndex)
                        .map(FileIndex::getFiles)
                        .flatMap(Set::stream)
                        .map(rf -> new RemoteFileAction(delete, rf))
                        .collect(Collectors.toList())));

    List<IndexRecord> records2 = create(saver2).toBlocking().last();

    FDXLoader loader = new FDXLoader(persist, first);
    List<FileIndex> loaded = create(loader).toList().toBlocking().last();
    assertThat(loaded).hasSize(0);
  }

  @Test
  public void deleteFirst() throws Exception {
    RemoteFile first = new RemoteFile();
    List<IndexRecord> records = storeFiles(first);

    FDXSaver saver =
        new FDXSaver(
            persist,
            new RemoteFileAction2Record(
                    first,
                    records.stream().map(IndexRecord::getFileIndex).collect(Collectors.toList()),
                    5)
                .call(
                    ImmutableList.of(
                        new RemoteFileAction(
                            delete, records.get(0).getFileIndex().getFiles().iterator().next()))));
    List<IndexRecord> records2 = create(saver).toBlocking().last();
    assertThat(
            records2
                .stream()
                .map(IndexRecord::getFileIndex)
                .map(FileIndex::getFiles)
                .map(Set::size)
                .mapToInt(Integer::intValue)
                .sum())
        .isEqualTo(99);

    FDXLoader loader = new FDXLoader(persist, first);
    List<FileIndex> loaded = create(loader).toList().toBlocking().last();
    assertThat(
            loaded
                .stream()
                .map(FileIndex::getFiles)
                .map(Set::size)
                .mapToInt(Integer::intValue)
                .sum())
        .isEqualTo(99);
  }

  @Test
  public void deleteFirstPage() throws Exception {
    RemoteFile first = new RemoteFile();
    List<IndexRecord> records = storeFiles(first);

    final Set<RemoteFile> toRemove = records.get(0).getFileIndex().getFiles();
    final Set<RemoteFile> toRemoveCopy =
        toRemove.stream().map(rf -> new RemoteFile().copyFrom(rf)).collect(Collectors.toSet());

    FDXSaver saver =
        new FDXSaver(
            persist,
            new RemoteFileAction2Record(
                    first,
                    records.stream().map(IndexRecord::getFileIndex).collect(Collectors.toList()),
                    5)
                .call(
                    toRemove
                        .stream()
                        .map(rf -> new RemoteFileAction(delete, rf))
                        .collect(Collectors.toList())));

    create(saver).toBlocking().last();

    FDXLoader loader = new FDXLoader(persist, first);
    List<FileIndex> loaded = create(loader).toList().toBlocking().last();
    assertThat(loaded).hasSize(19);
    assertThat(
            loaded
                .stream()
                .map(FileIndex::getFiles)
                .map(Set::size)
                .mapToInt(Integer::intValue)
                .sum())
        .isEqualTo(95);
    assertThat(
            loaded
                .stream()
                .map(FileIndex::getFiles)
                //check if contains removed files
                .anyMatch(
                    files -> toRemoveCopy.stream().filter(files::contains).findFirst().isPresent()))
        .isFalse();
  }
}
