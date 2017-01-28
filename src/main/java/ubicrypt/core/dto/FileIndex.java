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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileIndex {
  Set<RemoteFile> files = ConcurrentHashMap.newKeySet();
  RemoteFile nextIndex = new RemoteFile();

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

  public static final class FileIndexBuilder {
    Set<RemoteFile> files = ConcurrentHashMap.newKeySet();
    RemoteFile nextIndex;

    private FileIndexBuilder() {}

    public static FileIndexBuilder aFileIndex() {
      return new FileIndexBuilder();
    }

    public FileIndexBuilder withFiles(Set<RemoteFile> files) {
      this.files = files;
      return this;
    }

    public FileIndexBuilder addFile(RemoteFile file) {
      this.files.add(file);
      return this;
    }

    public FileIndexBuilder withNextIndex(RemoteFile nextIndex) {
      this.nextIndex = nextIndex;
      return this;
    }

    public FileIndex build() {
      FileIndex fileIndex = new FileIndex();
      fileIndex.setFiles(files);
      fileIndex.setNextIndex(nextIndex);
      return fileIndex;
    }
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("files", files)
        .append("nextIndex", nextIndex)
        .toString();
  }
}
