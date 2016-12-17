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

public class KeyConfig {
  KeyType type;
  String gpgSecring;
  String userId;

  public String getGpgSecring() {
    return gpgSecring;
  }

  public void setGpgSecring(final String gpgSecring) {
    this.gpgSecring = gpgSecring;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public KeyType getType() {
    return type;
  }

  public void setType(final KeyType type) {
    this.type = type;
  }

  enum KeyType {
    gpg,
    generated
  }
}
