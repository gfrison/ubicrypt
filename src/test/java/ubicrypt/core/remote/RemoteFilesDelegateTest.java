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

import com.google.common.collect.Iterators;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import rx.Observable;
import ubicrypt.core.Action;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.fdx.FDXLoader;
import ubicrypt.core.fdx.FDXSaver;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Persist;

import static com.google.common.collect.Iterators.getLast;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static ubicrypt.core.TestUtils.createDirs;
import static ubicrypt.core.TestUtils.deleteDirs;
import static ubicrypt.core.TestUtils.fileProvider;
import static ubicrypt.core.TestUtils.tmp;

public class RemoteFilesDelegateTest {
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

  @Test
  public void addMany() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(), 1);
    IntStream.range(0, 10).forEach(i -> d.add(new RemoteFile()));
    assertThat(d.getIndex().iterator()).hasSize(10);
  }

  @Test
  public void addAll() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(), 1);
    List<RemoteFile> l =
        IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    assertThat(d.getIndex().iterator()).hasSize(10);
    StreamSupport.stream(d.getIndex().spliterator(), false)
        .forEach(idx -> assertThat(idx.getFiles()).hasSize(1));
  }

  @Test
  public void removeLast() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(Action.add), 1);
    List<RemoteFile> l =
        IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, d.getIndex());
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    d =
        new RemoteFilesDelegate(
            Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last(), 1);
    assertThat(d.getIndex().iterator()).hasSize(10);
    assertThat(d.remove(getLast(getLast(d.getIndex().iterator()).getFiles().iterator()))).isTrue();
    Optional<RemoteFile> rf2 =
        Observable.create(new FDXSaver(persist, d.getIndex(), rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(9);
  }

  @Test
  public void removeFirst() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(Action.add), 1);
    List<RemoteFile> l =
        IntStream.range(0, 10)
            .mapToObj(
                i ->
                    new RemoteFile() {
                      {
                        setSize(i);
                      }
                    })
            .collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, d.getIndex());
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    d =
        new RemoteFilesDelegate(
            Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last(), 1);
    Set<RemoteFile> rfiles = d;
    assertThat(d.remove(rfiles.iterator().next())).isTrue();
    Optional<RemoteFile> rf2 =
        Observable.create(new FDXSaver(persist, d.getIndex(), rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(10);
    assertThat(loaded.getFiles()).isEmpty();
  }

  @Test
  public void removeMiddle() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(Action.add), 1);
    List<RemoteFile> l =
        IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, d.getIndex());
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    d =
        new RemoteFilesDelegate(
            Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last(), 1);

    assertThat(d.remove(Iterators.get(d.getIndex().iterator(), 5).getFiles().iterator().next()))
        .isTrue();
    Optional<RemoteFile> rf2 =
        Observable.create(new FDXSaver(persist, d.getIndex(), rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(9);
    assertThat(loaded.getFiles()).isNotEmpty();
  }

  @Test
  public void changeRemoteFile() {
    RemoteFilesDelegate d = new RemoteFilesDelegate(new FileIndex(Action.add), 1);
    List<RemoteFile> l =
        IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, d.getIndex());
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    d =
        new RemoteFilesDelegate(
            Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last(), 1);

    final RemoteFile remoteFile = Iterators.get(d.iterator(), 5);
    remoteFile.setSize(remoteFile.getSize() + 10);
    assertThat(Iterators.get(d.getIndex().iterator(), 5).getStatus()).isEqualTo(Action.update);
    assertThat(Iterators.get(d.getIndex().iterator(), 4).getStatus()).isEqualTo(Action.unchanged);
    assertThat(Iterators.get(d.getIndex().iterator(), 6).getStatus()).isEqualTo(Action.unchanged);
  }
}
