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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequestInitializer;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;

import rx.Observable;
import ubicrypt.core.Utils;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.slf4j.LoggerFactory.getLogger;

public class GDriveProvider extends UbiProvider implements DataStoreFactory {
  private static final Logger log = getLogger(GDriveProvider.class);
  public static String credentialId = "id";

  @JsonInclude private GDriveConf conf;
  private volatile Drive drive;
  private volatile HttpTransport http = new ApacheHttpTransport();

  public GDriveProvider(GDriveConf conf) {
    this.conf = conf;
  }

  public GDriveProvider() {}

  private static GoogleClientSecrets appCredentials() throws IOException {
    return GoogleClientSecrets.load(
        getDefaultInstance(),
        new InputStreamReader(GDriveProvider.class.getResourceAsStream("/google.json")));
  }

  @Override
  public Observable<ProviderStatus> init(long userId) {
    return Observable.create(
        subscriber -> {
          log.debug("init provider userId:{}", userId);
          try {
            GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        http,
                        getDefaultInstance(),
                        appCredentials(),
                        asList(DriveScopes.DRIVE_FILE))
                    .setDataStoreFactory(this)
                    .setAccessType("offline")
                    .build();
            conf.setDataStoreFactory(this);
            Credential credential = flow.loadCredential(credentialId);
            if (credential == null || credential.getRefreshToken() == null) {
              subscriber.onNext(ProviderStatus.unauthorized);
              return;
            }
            drive =
                new Drive.Builder(http, getDefaultInstance(), credential)
                    .setDriveRequestInitializer(new DriveRequestInitializer())
                    .setApplicationName("UbiCrypt")
                    .build();
            String email = null;
            try {
              email =
                  drive
                      .about()
                      .get()
                      .setFields("user, storageQuota")
                      .execute()
                      .getUser()
                      .getEmailAddress();
            } catch (GoogleJsonResponseException e) {
              if (e.getDetails().getCode() == 403) {
                drive = null;
                log.info("gdrive unauthorized:{}", e.getDetails().getMessage());
                subscriber.onNext(ProviderStatus.unauthorized);
                subscriber.onCompleted();
                return;
              }
            }
            if (isEmpty(conf.getEmail())) {
              conf.setEmail(email);
            } else if (!StringUtils.equals(conf.getEmail(), email)) {
              log.warn(
                  "this provider's gdrive email is not the same of the current google account");
              subscriber.onNext(ProviderStatus.unavailable);
            }
            File folder = new File();
            folder.setName(conf.getFolderName());
            folder.setMimeType("application/vnd.google-apps.folder");
            if (isEmpty(conf.getFolderId())) {
              log.debug("create new ubicrypt folder");
              File createdFolder = drive.files().create(folder).execute();
              conf.setFolderId(createdFolder.getId());
            } else {
              String folderId = conf.getFolderId();
              try {
                drive.files().get(folderId).execute();
                log.debug("ubicrypt folder already present");
              } catch (Exception e) {
                log.debug("create new ubicrypt folder");
                File createdFolder = drive.files().create(folder).execute();
                conf.setFolderId(createdFolder.getId());
              }
            }
            subscriber.onNext(ProviderStatus.initialized);
            subscriber.onCompleted();
          } catch (Exception e) {
            subscriber.onError(e);
          }
        });
  }

  @Override
  public String code() {
    return "gdrive";
  }

  @Override
  public Observable<String> post(InputStream is) {
    return Observable.create(
        subscriber -> {
          if (drive == null || conf == null || conf.getFolderId() == null) {
            subscriber.onError(new RuntimeException("gdrive not initialized"));
            return;
          }
          File file = new File();
          file.setParents(singletonList(conf.getFolderId()));
          try {
            String id =
                drive.files().create(file, new InputStreamContent(null, is)).execute().getId();
            subscriber.onNext(id);
            subscriber.onCompleted();
            Utils.close(is);
          } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 403) {
              //todo:quota exceeded
              log.warn(e.getDetails().getMessage());
              subscriber.onError(e);
              Utils.close(is);
            }
          } catch (IOException e) {
            subscriber.onError(e);
            Utils.close(is);
          }
        });
  }

  @Override
  public Observable<Boolean> delete(String pid) {
    return Observable.create(
        subscriber -> {
          try {
            if (drive == null) {
              subscriber.onError(new RuntimeException("gdrive not initialized"));
              return;
            }
            drive.files().delete(pid).execute();
            subscriber.onNext(true);
            subscriber.onCompleted();
          } catch (IOException e) {
            subscriber.onError(e);
          }
        });
  }

  @Override
  public Observable<Boolean> put(String pid, InputStream is) {
    return Observable.create(
        subscriber -> {
          if (drive == null) {
            subscriber.onError(new RuntimeException("gdrive not initialized"));
            return;
          }
          File file = new File();
          try {
            drive.files().update(pid, file, new InputStreamContent(null, is)).execute();
            subscriber.onNext(true);
            subscriber.onCompleted();
            Utils.close(is);
          } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 404) {
              subscriber.onError(new NotFoundException(pid));
            } else {
              subscriber.onError(e);
            }
            Utils.close(is);
          } catch (IOException e) {
            subscriber.onError(e);
            Utils.close(is);
          }
        });
  }

  @Override
  public Observable<InputStream> get(String pid) {
    return Observable.create(
        subscriber -> {
          if (drive == null) {
            subscriber.onError(new RuntimeException("gdrive not initialized"));
            return;
          }
          InputStream stream = null;
          try {
            try (InputStream is = drive.files().get(pid).executeMediaAsInputStream()) {
              stream = new ByteArrayInputStream(IOUtils.toByteArray(is));
            }
            subscriber.onNext(stream);
            subscriber.onCompleted();
          } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getCode() == 404) {
              subscriber.onError(new NotFoundException(pid));
            } else {
              subscriber.onError(e);
            }
            Utils.close(stream);
          } catch (IOException e) {
            subscriber.onError(e);
            Utils.close(stream);
          }
        });
  }

  @Override
  public String providerId() {
    return "gdrive://"
        + (conf == null
            ? "unconfigured"
            : conf.getEmail() == null ? "unconfigured" : conf.getEmail());
  }

  @Override
  public <V extends Serializable> DataStore<V> getDataStore(String id) throws IOException {
    return conf;
  }

  public GDriveConf getConf() {
    return conf;
  }

  public void setConf(GDriveConf conf) {
    this.conf = conf;
  }

  @Override
  public String toString() {
    return providerId();
  }

  @Override
  public Observable<Long> availableSpace() {
    return Observable.create(
        subscriber -> {
          if (drive == null) {
            subscriber.onError(new RuntimeException("gdrive not initialized"));
            return;
          }
          final About storageQuota;
          try {
            storageQuota = drive.about().get().setFields("storageQuota").execute();
          } catch (IOException e) {
            subscriber.onError(e);
            return;
          }
          Long limit = storageQuota.getStorageQuota().getLimit();
          if (limit == null) {
            subscriber.onNext(Long.MAX_VALUE);
            subscriber.onCompleted();
            return;
          }
          subscriber.onNext(limit - storageQuota.getStorageQuota().getUsage());
          subscriber.onCompleted();
        });
  }

  @JsonIgnore
  public Drive getDrive() {
    return drive;
  }
}
