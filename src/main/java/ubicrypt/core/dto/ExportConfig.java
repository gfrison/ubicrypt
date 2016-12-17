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

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.PGPKValue;

import static ubicrypt.core.Utils.copySynchronized;

public class ExportConfig {
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  private Set<UbiProvider> providers = ConcurrentHashMap.newKeySet();

  private Set<PGPKValue> ownedPKs = ConcurrentHashMap.newKeySet();

  public static ExportConfig copyFrom(LocalConfig config, PGPPublicKey ownedPK) {
    ExportConfig exp = new ExportConfig();
    exp.setOwnedPKs(config.getOwnedPKs());
    exp.setProviders(config.getProviders());
    exp.getOwnedPKs().add(new PGPKValue(ownedPK));
    return exp;
  }

  public Set<PGPKValue> getOwnedPKs() {
    return ownedPKs;
  }

  public void setOwnedPKs(Set<PGPKValue> ownedPKs) {
    this.ownedPKs = copySynchronized(ownedPKs);
  }

  public Set<UbiProvider> getProviders() {
    return providers;
  }

  public void setProviders(Set<UbiProvider> providers) {
    this.providers = copySynchronized(providers);
  }
}
