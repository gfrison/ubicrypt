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

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Persist;

import static java.lang.String.valueOf;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static rx.Observable.create;
import static ubicrypt.core.TestUtils.createDirs;
import static ubicrypt.core.TestUtils.deleteDirs;
import static ubicrypt.core.TestUtils.fileProvider;
import static ubicrypt.core.TestUtils.tmp;
import static ubicrypt.core.dto.FileIndex.FileIndexBuilder.aFileIndex;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.created;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.modified;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.unchanged;

public class FDXSaverTest {
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
  public void empty() throws Exception {
    FDXSaver saver = new FDXSaver(persist, new FileIndex());
    assertThat(create(saver).defaultIfEmpty(null).toBlocking().last()).isNull();
  }

  @Test
  public void one() throws Exception {
    FileIndex fi = aFileIndex()
        .withFiles(ImmutableSet.of(new RemoteFile()))
        .withStatus(Action.add)
        .build();
    FDXSaver saver = new FDXSaver(persist, fi);
    final RemoteFile last = create(saver).toBlocking().last();
    assertThat(last).isNotNull();
    assertThat(last.getRemoteName()).isNotEmpty();
    FileIndex ret = persist.getObject(last, FileIndex.class).toBlocking().last();
    assertThat(ret.getFiles().size()).isEqualTo(1);
    assertThat(ret.getNextIndex().getName()).isNull();
  }

  @Test
  public void manyNewFiles() throws Exception {
    FileIndex fi = createUnsaved(10);
    FDXSaver saver = new FDXSaver(persist, fi);
    create(saver)
    assertThat(irecord.get(0).getRemoteFile().getRemoteName()).isNotEmpty();
    range(0, 10)
        .forEach(
            i ->
                assertThat(
                    persist
                        .getObject(irecord.get(i).getRemoteFile(), FileIndex.class)
                        .toBlocking()
                        .last()
                        .getFiles())
                    .hasSize(1));

    range(0, 10)
        .forEach(
            i -> {
              final IndexRecord record = irecord.get(i);
              assertThat(Files.exists(tmp.resolve(record.getRemoteFile().getRemoteName())));
            });
  }

  public FileIndex createUnsaved(int num) {
    final FileIndex index = aFileIndex()
        .withFiles(
            ImmutableSet.of(
                new RemoteFile() {
                  {
                    setRemoteName(valueOf(num));
                  }
                }))
        .withStatus(Action.add)
        .build();
    if (num == 0) {
      return index;
    }
    FileIndex created = createFI(num - 1);
    index.setNext(created);
    created.setParent(index);
    return index;
  }

  public RemoteFile create10() {
    return createN(10)
        .map(Tuple2::getT2)
        .toBlocking().last();
  }

  public Observable<Tuple2<FileIndex, RemoteFile>> createN(int num) {
    final FileIndex index = aFileIndex()
        .withFiles(
            ImmutableSet.of(
                new RemoteFile() {
                  {
                    setRemoteName(valueOf(num));
                  }
                }))
        .build();
    if (num == 0) {
      RemoteFile rf = new RemoteFile();
      return persist.put(index, rf)
          .map(res -> Tuple.of(index, rf));
    }

    return createN(num - 1)
        .flatMap(tupla -> {
          index.setNextIndex(tupla.getT2());
          index.setNext(tupla.getT1());
          tupla.getT1().setParent(index);
          RemoteFile rf = new RemoteFile();
          return persist.put(index, rf)
              .map(res -> Tuple.of(index, rf));
        });
  }

  @Test
  public void addNewFile() throws Exception {
    List<IndexRecord> records = create10();
    records.forEach(r -> r.setStatus(unchanged));
    records.add(
        new IndexRecord(
            aFileIndex()
                .addFile(
                    new RemoteFile() {
                      {
                        setRemoteName("10");
                      }
                    })
                .build(),
            new RemoteFile(),
            created));
    FDXSaver save = new FDXSaver(persist, records);
    assertThat(create(save).toBlocking().last()).hasSize(11);
    assertThat(Files.exists(tmp.resolve(records.get(10).getRemoteFile().getRemoteName())));
    Iterator<IndexRecord> it = records.iterator();
    RemoteFile rf = records.get(0).getRemoteFile();
    int i = 0;
    while (it.hasNext()) {
      IndexRecord ir = it.next();
      assertThat(Files.exists(tmp.resolve(rf.getRemoteName()))).isTrue();
      assertThat(
          persist
              .getObject(rf, FileIndex.class)
              .toBlocking()
              .last()
              .getFiles()
              .iterator()
              .next()
              .getRemoteName())
          .isEqualTo(valueOf(i++));
      rf = ir.getFileIndex().getNextIndex();
    }
  }

  @Test
  public void modify1File() throws Exception {
    List<IndexRecord> records = create10();
    records.forEach(r -> r.setStatus(unchanged));
    records
        .get(0)
        .getFileIndex()
        .setFiles(
            ImmutableSet.of(
                records.get(0).getFileIndex().getFiles().iterator().next(), new RemoteFile()));
    records
        .get(1)
        .getFileIndex()
        .setFiles(
            ImmutableSet.of(
                records.get(1).getFileIndex().getFiles().iterator().next(), new RemoteFile()));
    records.get(1).setStatus(modified);

    FDXSaver saver = new FDXSaver(persist, records);
    assertThat(create(saver).toBlocking().last()).hasSize(10);
    assertThat(
        persist
            .getObject(records.get(0).getRemoteFile(), FileIndex.class)
            .toBlocking()
            .last()
            .getFiles())
        .hasSize(1);
    assertThat(
        persist
            .getObject(records.get(1).getRemoteFile(), FileIndex.class)
            .toBlocking()
            .last()
            .getFiles())
        .hasSize(2);
  }
}
