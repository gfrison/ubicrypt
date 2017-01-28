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

import ubicrypt.core.dto.RemoteFile;

public class RemoteFileAction {
  public RemoteFileAction(Action action, RemoteFile remoteFile) {
    this.action = action;
    this.remoteFile = remoteFile;
  }

  public enum Action {
    add,
    update,
    delete
  }

  private final Action action;
  private final RemoteFile remoteFile;

  public Action getAction() {
    return action;
  }

  public RemoteFile getRemoteFile() {
    return remoteFile;
  }
}
