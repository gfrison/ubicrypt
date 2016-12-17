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

import java.time.Instant;
import java.util.Date;

public class ProviderLock {
  private int deviceId;
  private Instant expires;

  public ProviderLock() {}

  public ProviderLock(final int deviceId, final Instant expires) {
    this.deviceId = deviceId;
    this.expires = expires;
  }

  public ProviderLock(final int deviceId, final Date expires) {
    this.deviceId = deviceId;
    this.expires = expires.toInstant();
  }

  public int getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(int deviceId) {
    this.deviceId = deviceId;
  }

  public Instant getExpires() {
    return expires;
  }

  public void setExpires(Instant expires) {
    this.expires = expires;
  }

  @Override
  public String toString() {
    return "ProviderLock{" + "deviceId=" + deviceId + ", expires=" + expires + '}';
  }
}
