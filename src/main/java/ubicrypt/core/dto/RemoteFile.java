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

import java.util.Optional;

import ubicrypt.core.crypto.AESGCM;

public class RemoteFile extends UbiFile<RemoteFile> {
  private Key key = new Key(AESGCM.rndKey());
  private String remoteName;
  //on uploading error mark it: true
  private boolean error = false;

  public static RemoteFile createFrom(UbiFile file) {
    RemoteFile ret = new RemoteFile();
    ret.copyFrom(file);
    return ret;
  }

  public RemoteFile copyFrom(RemoteFile file) {
    super.copyFrom(file);
    key = file.getKey();
    remoteName = file.getRemoteName();
    error = file.isError();
    return this;
  }

  public Key getKey() {
    return key;
  }

  public void setKey(Key key) {
    this.key = key;
  }

  public String getRemoteName() {
    return remoteName;
  }

  public void setRemoteName(String remoteName) {
    this.remoteName = remoteName;
  }

  @Override
  public Optional<Key> getEncryption() {
    return Optional.ofNullable(key);
  }

  @Override
  public String getName() {
    return getRemoteName();
  }

  public boolean isError() {
    return error;
  }

  public void setError(boolean error) {
    this.error = error;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("remoteName", remoteName)
        .append("error", error)
        .append("vclock", vclock)
        .toString();
  }
}
