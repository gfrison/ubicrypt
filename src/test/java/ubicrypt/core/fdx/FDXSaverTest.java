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
import com.google.common.collect.Iterators;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import rx.Observable;
import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Persist;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static rx.Observable.create;
import static ubicrypt.core.TestUtils.createDirs;
import static ubicrypt.core.TestUtils.deleteDirs;
import static ubicrypt.core.TestUtils.fileProvider;
import static ubicrypt.core.TestUtils.tmp;
import static ubicrypt.core.dto.FileIndex.FileIndexBuilder.aFileIndex;

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
    assertThat(create(saver).defaultIfEmpty(null).toBlocking().last()).isNotEmpty();
  }

  @Test
  public void one() throws Exception {
    FileIndex fi =
        aFileIndex().withFiles(ImmutableSet.of(new RemoteFile())).withStatus(Action.add).build();
    FDXSaver saver = new FDXSaver(persist, fi);
    final RemoteFile last = create(saver).toBlocking().last().get();
    assertThat(last).isNotNull();
    assertThat(last.getRemoteName()).isNotEmpty();
    FileIndex ret = persist.getObject(last, FileIndex.class).toBlocking().last();
    assertThat(ret.getFiles().size()).isEqualTo(1);
    assertThat(ret.getNextIndex()).isNull();
  }

  @Test
  public void oneAlreadyExists() throws Exception {
    FileIndex fi =
        aFileIndex().withFiles(ImmutableSet.of(new RemoteFile())).withStatus(Action.update).build();
    RemoteFile rf = new RemoteFile();
    persist.put(fi, rf).toBlocking().last();
    FDXSaver saver = new FDXSaver(persist, fi, rf);
    Optional<RemoteFile> last = create(saver).toBlocking().last();
    assertThat(last).isNotNull();
    assertThat(last).isEmpty();
    FileIndex ret = persist.getObject(rf, FileIndex.class).toBlocking().last();
    assertThat(ret.getFiles().size()).isEqualTo(1);
    assertThat(ret.getNextIndex()).isNull();
  }

  @Test
  public void manyNewFiles() throws Exception {
    FileIndex fi = createUnsaved(3);
    FDXSaver saver = new FDXSaver(persist, fi);
    RemoteFile rf = create(saver).toBlocking().last().get();
    assertThat(rf).isNotNull();
    assertThat(Files.list(tmp)).hasSize(3);
  }

  public FileIndex createUnsaved(int num) {
    final FileIndex index =
        aFileIndex()
            .withFiles(
                ImmutableSet.of(
                    new RemoteFile() {
                      {
                        setRemoteName(valueOf(num));
                      }
                    }))
            .withStatus(Action.add)
            .build();
    if (num == 1) {
      return index;
    }
    FileIndex created = createUnsaved(num - 1);
    index.setNext(created);
    created.setParent(index);
    return index;
  }

  public RemoteFile create10() {
    return createN(10).map(Tuple2::getT2).toBlocking().last();
  }

  public Observable<Tuple2<FileIndex, RemoteFile>> createN(int num) {
    final FileIndex index =
        aFileIndex()
            .withFiles(
                ImmutableSet.of(
                    new RemoteFile() {
                      {
                        setRemoteName(valueOf(num));
                      }
                    }))
            .build();
    if (num == 1) {
      RemoteFile rf = new RemoteFile();
      return persist.put(index, rf).map(res -> Tuples.of(index, rf));
    }

    return createN(num - 1)
        .flatMap(
            tupla -> {
              index.setNextIndex(tupla.getT2());
              index.setNext(tupla.getT1());
              tupla.getT1().setParent(index);
              RemoteFile rf = new RemoteFile();
              return persist.put(index, rf).map(res -> Tuples.of(index, rf));
            });
  }

  @Test
  public void addNewFile() throws Exception {
    RemoteFile rf = create10();
    FDXLoader loader = new FDXLoader(persist, rf);
    FileIndex fi = create(loader).toBlocking().first();
    assertThat(fi.iterator()).hasSize(10);
    FileIndex last = Iterators.getLast(fi.iterator());
    last.setNext(
        aFileIndex()
            .withFiles(Set.of(new RemoteFile()))
            .withStatus(Action.add)
            .withParent(last)
            .build());
    Optional<RemoteFile> opt = create(new FDXSaver(persist, fi, rf)).toBlocking().last();
    assertThat(opt).isEmpty();
    assertThat(Files.list(tmp)).hasSize(11);
    fi = create(loader).toBlocking().last();
    last = Iterators.getLast(fi.iterator());
    assertThat(last.getFiles()).hasSize(1);
  }

  @Test
  public void modify1File() throws Exception {
    RemoteFile rf = create10();
    FDXLoader loader = new FDXLoader(persist, rf);
    FileIndex parent = create(loader).toBlocking().last();
    FileIndex fi = Iterators.get(parent.iterator(), 5);
    fi.getFiles().add(new RemoteFile());
    fi.setStatus(Action.update);
    Optional<RemoteFile> opt = create(new FDXSaver(persist, parent, rf)).toBlocking().last();
    assertThat(opt).isEmpty();
    assertThat(Files.list(tmp)).hasSize(10);
    parent = create(loader).toBlocking().last();
    fi = Iterators.get(parent.iterator(), 5);
    assertThat(fi.getFiles()).hasSize(2);
  }

  @Test
  public void deleteLastFile() throws IOException {
    RemoteFile rf = create10();
    FDXLoader loader = new FDXLoader(persist, rf);
    FileIndex parent = create(loader).toBlocking().last();
    Iterators.getLast(parent.iterator()).setStatus(Action.delete);
    Optional<RemoteFile> opt = create(new FDXSaver(persist, parent, rf)).toBlocking().last();
    assertThat(opt).isEmpty();
    assertThat(Files.list(tmp)).hasSize(9);
    parent = create(loader).toBlocking().last();
    assertThat(parent.iterator()).hasSize(9);
  }

  @Test
  public void deleteIntermediateFile() throws IOException {
    RemoteFile rf = create10();
    FDXLoader loader = new FDXLoader(persist, rf);
    FileIndex parent = create(loader).toBlocking().last();
    Iterators.get(parent.iterator(), 5).setStatus(Action.delete);
    Optional<RemoteFile> opt = create(new FDXSaver(persist, parent, rf)).toBlocking().last();
    assertThat(opt).isEmpty();
    assertThat(Files.list(tmp)).hasSize(9);
    parent = create(loader).toBlocking().last();
    assertThat(parent.iterator()).hasSize(9);
  }
}
