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
package ubicrypt.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ubicrypt.core.Action;

public class FileIndex implements Iterable<FileIndex> {
  private Set<RemoteFile> files = new LinkedHashSet<>();
  private RemoteFile nextIndex;
  @JsonIgnore private volatile Action status = Action.unchanged;
  @JsonIgnore private volatile FileIndex next;
  @JsonIgnore private volatile FileIndex parent;

  public FileIndex() {}

  public FileIndex(Action status) {
    this.status = status;
  }

  public FileIndex getParent() {
    return parent;
  }

  public void setParent(FileIndex parent) {
    this.parent = parent;
  }

  public Action getStatus() {
    return status;
  }

  public void setStatus(Action status) {
    this.status = status;
  }

  public FileIndex getNext() {
    return next;
  }

  public void setNext(FileIndex next) {
    this.next = next;
  }

  public Set<RemoteFile> getFiles() {
    return files;
  }

  public void setFiles(Set<RemoteFile> files) {
    this.files = files;
  }

  public RemoteFile getNextIndex() {
    return nextIndex;
  }

  public void setNextIndex(RemoteFile nextIndex) {
    this.nextIndex = nextIndex;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("files", files)
        .append("nextIndex", nextIndex)
        .toString();
  }

  @Override
  public Iterator<FileIndex> iterator() {
    final FileIndex fi = this;
    return new Iterator<>() {
      private FileIndex el = fi;

      @Override
      public boolean hasNext() {
        return el != null;
      }

      @Override
      public FileIndex next() {
        FileIndex ret = el;
        el = el.next;
        return ret;
      }
    };
  }

  public static final class FileIndexBuilder {
    private Set<RemoteFile> files = ConcurrentHashMap.newKeySet();
    private RemoteFile nextIndex;
    private volatile Action status = Action.unchanged;
    private volatile FileIndex next;
    private volatile FileIndex parent;

    private FileIndexBuilder() {}

    public static FileIndexBuilder aFileIndex() {
      return new FileIndexBuilder();
    }

    public FileIndexBuilder withFiles(Set<RemoteFile> files) {
      this.files = files;
      return this;
    }

    public FileIndexBuilder withNextIndex(RemoteFile nextIndex) {
      this.nextIndex = nextIndex;
      return this;
    }

    public FileIndexBuilder withStatus(Action status) {
      this.status = status;
      return this;
    }

    public FileIndexBuilder withNext(FileIndex next) {
      this.next = next;
      return this;
    }

    public FileIndexBuilder withParent(FileIndex parent) {
      this.parent = parent;
      return this;
    }

    public FileIndex build() {
      FileIndex fileIndex = new FileIndex();
      fileIndex.setFiles(files);
      fileIndex.setNextIndex(nextIndex);
      fileIndex.setStatus(status);
      fileIndex.setNext(next);
      fileIndex.setParent(parent);
      return fileIndex;
    }
  }
}
