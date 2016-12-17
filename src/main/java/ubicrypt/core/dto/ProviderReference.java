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

public class ProviderReference {
  private String code;
  private RemoteFile fileIndexFile =
      new RemoteFile() {
        {
          setKey(
              new Key() {
                {
                  setType(UbiFile.KeyType.pgp);
                }
              });
        }
      };
  private RemoteFile confFile =
      new RemoteFile() {
        {
          setKey(
              new Key() {
                {
                  setType(UbiFile.KeyType.pgp);
                }
              });
        }
      };
  private RemoteFile lockFile =
      new RemoteFile() {
        {
          setKey(
              new Key() {
                {
                  setType(UbiFile.KeyType.pgp);
                }
              });
        }
      };

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public RemoteFile getFileIndexFile() {
    return fileIndexFile;
  }

  public void setFileIndexFile(RemoteFile fileIndexFile) {
    this.fileIndexFile = fileIndexFile;
  }

  public RemoteFile getConfFile() {
    return confFile;
  }

  public void setConfFile(RemoteFile confFile) {
    this.confFile = confFile;
  }

  public RemoteFile getLockFile() {
    return lockFile;
  }

  public void setLockFile(RemoteFile lockFile) {
    this.lockFile = lockFile;
  }
}
