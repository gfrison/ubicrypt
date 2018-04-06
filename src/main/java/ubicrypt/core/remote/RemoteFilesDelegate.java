package ubicrypt.core.remote;

import com.google.common.collect.ForwardingSet;

import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.Action.delete;
import static ubicrypt.core.Action.update;

public class RemoteFilesDelegate extends ForwardingSet<RemoteFile> {
  private static final Logger log = getLogger(RemoteFilesDelegate.class);
  private final Set<RemoteFile> delegate;
  private final int maxFilesPerIndex;
  private final FileIndex index;

  public RemoteFilesDelegate(FileIndex index, int maxFilesPerIndex) {
    this.index = index;
    this.delegate = new HashSet<>(StreamSupport.stream(index.spliterator(), false)
        .map(FileIndex::getFiles)
        .flatMap(Set::stream)
        .collect(Collectors.toSet()));
    this.maxFilesPerIndex = maxFilesPerIndex;
  }

  @Override
  protected Set<RemoteFile> delegate() {
    return delegate;
  }

  @Override
  public boolean add(RemoteFile element) {
    List<FileIndex> indexes = indexes();
    Optional<FileIndex> idx = indexes.stream()
        .filter(fi -> fi.getFiles().size() < maxFilesPerIndex)
        .findFirst();
    if (idx.isPresent()) {
      final FileIndex fileIndex = idx.get();
      fileIndex.getFiles().add(element);
      fileIndex.setStatus(update);
    } else {
      FileIndex last = indexes.get(indexes.size() - 1);
      last.setStatus(update);
      final FileIndex nfi = new FileIndex();
      last.setNext(nfi);
      nfi.setParent(last);
      nfi.getFiles().add(element);
    }
    return delegate.add(element);
  }

  @Override
  public boolean remove(Object object) {
    List<FileIndex> indexes = indexes();
    Optional<FileIndex> idx = indexes.stream()
        .filter(fi -> fi.getFiles().contains(object))
        .findFirst();
    if (!idx.isPresent()) {
      log.warn("no index present for remote file:{}", object);
      return false;
    }
    final FileIndex fileIndex = idx.get();
    fileIndex.getFiles().remove(object);
    if (fileIndex.getFiles().isEmpty()) {
      if (fileIndex.getParent() == null) {
        fileIndex.setStatus(update);
      } else {
        fileIndex.setStatus(delete);
        fileIndex.getParent().setStatus(update);
      }
    }
    return delegate.remove(object);
  }

  private List<FileIndex> indexes() {
    return StreamSupport.stream(index.spliterator(), false)
        .collect(Collectors.toList());
  }

}
