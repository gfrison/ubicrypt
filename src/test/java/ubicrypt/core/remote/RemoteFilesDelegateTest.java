package ubicrypt.core.remote;

import com.google.common.collect.Iterators;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
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
    final FileIndex fi = new FileIndex();
    RemoteFilesDelegate d = new RemoteFilesDelegate(fi, 1);
    IntStream.range(0, 10).forEach(i -> d.add(new RemoteFile()));
    assertThat(fi.iterator()).hasSize(10);
  }

  @Test
  public void addAll() {
    final FileIndex fi = new FileIndex();
    RemoteFilesDelegate d = new RemoteFilesDelegate(fi, 1);
    List<RemoteFile> l = IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    assertThat(fi.iterator()).hasSize(10);
    StreamSupport.stream(fi.spliterator(), false)
        .forEach(idx -> assertThat(idx.getFiles()).hasSize(1));
  }

  @Test
  public void removeLast() {
    final FileIndex fi = new FileIndex(Action.add);
    RemoteFilesDelegate d = new RemoteFilesDelegate(fi, 1);
    List<RemoteFile> l = IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, fi);
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    d = new RemoteFilesDelegate(loaded, 1);
    assertThat(loaded.iterator()).hasSize(10);
    assertThat(d.remove(getLast(getLast(loaded.iterator()).getFiles().iterator()))).isTrue();
    Optional<RemoteFile> rf2 = Observable.create(new FDXSaver(persist,loaded,rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(9);
  }

  @Test
  public void removeFirst() {
    final FileIndex fi = new FileIndex(Action.add);
    RemoteFilesDelegate d = new RemoteFilesDelegate(fi, 1);
    List<RemoteFile> l = IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, fi);
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    d = new RemoteFilesDelegate(loaded, 1);

    assertThat(d.remove(loaded.getFiles().iterator().next())).isTrue();
    Optional<RemoteFile> rf2 = Observable.create(new FDXSaver(persist,loaded,rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(10);
    assertThat(loaded.getFiles()).isEmpty();


  }
  @Test
  public void removeMiddle() {
    final FileIndex fi = new FileIndex(Action.add);
    RemoteFilesDelegate d = new RemoteFilesDelegate(fi, 1);
    List<RemoteFile> l = IntStream.range(0, 10).mapToObj(i -> new RemoteFile()).collect(Collectors.toList());
    d.addAll(l);
    FDXSaver saver = new FDXSaver(persist, fi);
    Optional<RemoteFile> rf = Observable.create(saver).toBlocking().last();
    assertThat(rf.isPresent()).isTrue();
    FileIndex loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    d = new RemoteFilesDelegate(loaded, 1);

    assertThat(d.remove(Iterators.get(loaded.iterator(),5).getFiles().iterator().next())).isTrue();
    Optional<RemoteFile> rf2 = Observable.create(new FDXSaver(persist,loaded,rf.get())).toBlocking().last();
    assertThat(rf2.isPresent()).isFalse();
    loaded = Observable.create(new FDXLoader(persist, rf.get())).toBlocking().last();
    assertThat(loaded.iterator()).hasSize(9);
    assertThat(loaded.getFiles()).isNotEmpty();


  }
}
