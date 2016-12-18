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

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.InputStream;

import rx.Observable;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;

public abstract class UbiProvider {
  private transient long userId = -1;
  private RemoteFile indexListFile =
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

  public abstract Observable<String> post(InputStream is);

  public abstract Observable<Boolean> delete(final String pid);

  public abstract Observable<Boolean> put(final String pid, final InputStream is);

  public abstract Observable<InputStream> get(final String pid);

  public abstract String providerId();

  public Observable<ProviderStatus> init(final long userId) {
    this.userId = userId;
    return Observable.just(ProviderStatus.initialized);
  }

  public RemoteFile getConfFile() {
    return confFile;
  }

  public void setConfFile(final RemoteFile confFile) {
    this.confFile = confFile;
  }

  public RemoteFile getLockFile() {
    return lockFile;
  }

  public void setLockFile(final RemoteFile lockFile) {
    this.lockFile = lockFile;
  }

  public RemoteFile getIndexListFile() {
    return indexListFile;
  }

  public void setIndexListFile(RemoteFile indexListFile) {
    this.indexListFile = indexListFile;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    final UbiProvider that = (UbiProvider) o;

    return new EqualsBuilder().append(providerId(), that.providerId()).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(providerId()).toHashCode();
  }

  @JsonIgnore
  public long getDelayAcquiringLockMs() {
    return 2000;
  }

  @JsonIgnore
  public long getDurationLockMs() {
    //5mins
    return 5 * 60 * 1000;
  }

  public void close() {}

  public abstract String code();

  public Observable<Long> availableSpace() {
    return Observable.just(Long.MAX_VALUE);
  }
}
