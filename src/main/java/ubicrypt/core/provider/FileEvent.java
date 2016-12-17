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
package ubicrypt.core.provider;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import ubicrypt.core.dto.UbiFile;

public class FileEvent {
  private final Type type;
  private final Location location;
  private final UbiFile localFile;

  public FileEvent(final UbiFile localFile, final Type type, final Location location) {
    this.localFile = localFile;
    this.type = type;
    this.location = location;
  }

  public Location getLocation() {
    return location;
  }

  public UbiFile getFile() {
    return localFile;
  }

  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
      .append("type", type)
      .append("location", location)
      .append("localFile", localFile.getPath())
      .toString();
  }

  public enum Type {
    updated,
    removed,
    deleted,
    created,
    error,
    synched,
    unsynched
  }

  public enum Location {
    local,
    remote
  }
}
