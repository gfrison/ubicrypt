package ubicrypt.core.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ubicrypt.core.provider.UbiProvider;

import static ubicrypt.core.Utils.copySynchronized;

public class StoredRemoteConfig {
  @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
  private Set<UbiProvider> providers = ConcurrentHashMap.newKeySet();
  private RemoteFile index = new RemoteFile();

  public Set<UbiProvider> getProviders() {
    return providers;
  }

  public void setProviders(Set<UbiProvider> providers) {
    this.providers = copySynchronized(providers);
  }

  public RemoteFile getIndex() {
    return index;
  }

  public void setIndex(RemoteFile index) {
    this.index = index;
  }
}
