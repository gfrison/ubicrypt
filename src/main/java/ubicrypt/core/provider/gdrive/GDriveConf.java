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
package ubicrypt.core.provider.gdrive;

import com.google.api.client.util.IOUtils;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.common.base.Throwables;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GDriveConf implements DataStore {
  private String id;
  private String folderName = "ubicrypt";

  private Map<String, byte[]> map = new ConcurrentHashMap<>();
  @JsonIgnore private DataStoreFactory dataStoreFactory;
  private String folderId;
  private String email;

  public GDriveConf() {}

  public GDriveConf(String id, DataStoreFactory dataStoreFactory) {
    this.id = id;
    this.dataStoreFactory = dataStoreFactory;
  }

  @Override
  public DataStoreFactory getDataStoreFactory() {
    return dataStoreFactory;
  }

  public void setDataStoreFactory(DataStoreFactory dataStoreFactory) {
    this.dataStoreFactory = dataStoreFactory;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public int size() throws IOException {
    return map.size();
  }

  @Override
  public boolean isEmpty() throws IOException {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(String key) throws IOException {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Serializable value) throws IOException {
    if (value == null) {
      return false;
    }
    byte[] serialized = IOUtils.serialize(value);
    return map.values()
        .stream()
        .map(bs -> Arrays.equals(serialized, bs))
        .filter(test -> test)
        .findFirst()
        .orElse(false);
  }

  @Override
  public Set<String> keySet() throws IOException {
    return map.keySet();
  }

  @Override
  public Collection values() throws IOException {
    return map.values()
        .stream()
        .map(
            bs -> {
              try {
                return IOUtils.deserialize(bs);
              } catch (IOException e) {
                Throwables.propagate(e);
              }
              return null;
            })
        .collect(Collectors.toList());
  }

  @Override
  public Serializable get(String key) throws IOException {
    return IOUtils.deserialize(map.get(key));
  }

  @Override
  public DataStore set(String key, Serializable value) throws IOException {
    map.put(key, IOUtils.serialize(value));
    return this;
  }

  @Override
  public DataStore clear() throws IOException {
    map.clear();
    return this;
  }

  @Override
  public DataStore delete(String key) throws IOException {
    map.remove(key);
    return this;
  }

  public Map<String, byte[]> getMap() {
    return map;
  }

  public void setMap(Map<String, byte[]> map) {
    this.map = map;
  }

  public String getFolderName() {
    return folderName;
  }

  public void setFolderName(String folderName) {
    this.folderName = folderName;
  }

  public String getFolderId() {
    return folderId;
  }

  public void setFolderId(String folderId) {
    this.folderId = folderId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}
