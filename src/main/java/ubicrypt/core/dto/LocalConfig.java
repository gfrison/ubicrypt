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

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.PGPKValue;

import static ubicrypt.core.Utils.copySynchronized;

public class LocalConfig {

  private int version = 1;

  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  private Set<UbiProvider> providers = ConcurrentHashMap.newKeySet();

  private Set<LocalFile> localFiles = ConcurrentHashMap.newKeySet();
  private Set<Path> trackedFolders = ConcurrentHashMap.newKeySet();
  private Set<PGPKValue> ownedPKs = ConcurrentHashMap.newKeySet();

  public Set<UbiProvider> getProviders() {
    return providers;
  }

  public void setProviders(final Set<UbiProvider> providers) {
    this.providers = copySynchronized(providers);
  }

  public Set<LocalFile> getLocalFiles() {
    return localFiles;
  }

  public void setLocalFiles(final Set<LocalFile> localFiles) {
    this.localFiles = copySynchronized(localFiles);
  }

  public Set<Path> getTrackedFolders() {
    return trackedFolders;
  }

  public void setTrackedFolders(Set<Path> trackedFolders) {
    this.trackedFolders = copySynchronized(trackedFolders);
  }

  public Set<PGPKValue> getOwnedPKs() {
    return ownedPKs;
  }

  public void setOwnedPKs(Set<PGPKValue> ownedPKs) {
    this.ownedPKs = copySynchronized(ownedPKs);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("localFiles", localFiles)
        .append("version", version)
        .append("providers", providers)
        .append("trackedFolders", trackedFolders)
        .toString();
  }
}
