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
package ubicrypt.core.remote;

import org.slf4j.Logger;

import java.util.Set;

import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.UbiProvider;

import static org.slf4j.LoggerFactory.getLogger;

public class RemoteConfig {
  private static final Logger log = getLogger(RemoteConfig.class);
  private final Set<UbiProvider> providers;
  private final int maxFilesPerIndex;
  private final RemoteFile indexFile;
  private final FileIndex index;

  public RemoteConfig() {
    providers = null;
    indexFile = null;
    index = new FileIndex();
    maxFilesPerIndex = 100;
  }

  public RemoteConfig(Set<UbiProvider> providers) {
    indexFile = null;
    index = new FileIndex();
    maxFilesPerIndex = 100;
    this.providers = providers;
  }

  public RemoteConfig(Set<UbiProvider> providers, FileIndex index, RemoteFile indexFile, int maxFilesPerIndex) {
    this.indexFile = indexFile;
    this.providers = providers;
    this.index = index;
    this.maxFilesPerIndex = maxFilesPerIndex;
  }


  public Set<UbiProvider> getProviders() {
    return providers;
  }


  public synchronized Set<RemoteFile> getRemoteFiles() {
    return new RemoteFilesDelegate(index, maxFilesPerIndex);
  }

  public RemoteFile getIndexFile() {
    return indexFile;
  }

  public FileIndex getIndex() {
    return index;
  }
}
