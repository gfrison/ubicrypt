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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;

public class IndexRecord {
  public enum IRStatus {
    unchanged,
    modified,
    created,
    deleted
  }

  private IRStatus status = IRStatus.unchanged;
  private FileIndex fileIndex;
  private RemoteFile remoteFile;

  public IndexRecord(FileIndex fileIndex, RemoteFile remoteFile) {
    this.fileIndex = fileIndex;
    this.remoteFile = remoteFile;
  }

  public IndexRecord(FileIndex fileIndex, RemoteFile remoteFile, IRStatus status) {
    this.status = status;
    this.fileIndex = fileIndex;
    this.remoteFile = remoteFile;
  }

  public IRStatus getStatus() {
    return status;
  }

  public void setStatus(IRStatus status) {
    this.status = status;
  }

  public FileIndex getFileIndex() {
    return fileIndex;
  }

  public void setFileIndex(FileIndex fileIndex) {
    this.fileIndex = fileIndex;
  }

  public RemoteFile getRemoteFile() {
    return remoteFile;
  }

  public void setRemoteFile(RemoteFile remoteFile) {
    this.remoteFile = remoteFile;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("status", status)
        .append("fileIndex", fileIndex)
        .append("remoteFile", remoteFile)
        .toString();
  }
}
