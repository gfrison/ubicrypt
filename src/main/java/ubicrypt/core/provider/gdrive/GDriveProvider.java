package ubicrypt.core.provider.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequestInitializer;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import rx.Observable;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance;
import static java.util.Arrays.asList;

public class GDriveProvider extends UbiProvider implements DataStoreFactory {

    @JsonInclude
    private Set<GDriveConf> confs = ConcurrentHashMap.newKeySet();
    private volatile Drive drive;
    private volatile AtomicReference<Credential> credential = new AtomicReference<>();
    private volatile String folderId;

    public GDriveProvider(GDriveConf conf) {
        confs.add(conf);
    }

    @Override
    public Observable<ProviderStatus> init(long userId) {
        return Observable.create(subscriber -> {
            try {
                ApacheHttpTransport http = new ApacheHttpTransport();
                GoogleAuthorizationCodeFlow flow =
                        new GoogleAuthorizationCodeFlow.Builder(
                                http, getDefaultInstance(), appCredentials(), asList(DriveScopes.DRIVE_FILE))
                                .setDataStoreFactory(this)
                                .setAccessType("offline")
                                .build();
                confs.stream().forEach(c -> c.setDataStoreFactory(this));
                credential.set(flow.loadCredential(String.valueOf(userId)));
                if (credential.get() == null || credential.get().getRefreshToken() == null) {
                    subscriber.onNext(ProviderStatus.unavailable);
                    return;
                }

                drive = new Drive.Builder(http, getDefaultInstance(), credential.get())
                        .setDriveRequestInitializer(new DriveRequestInitializer())
                        .setApplicationName("UbiCrypt").build();
                File folder = new File();
                folder.setName("ubicrypt");
                folder.setMimeType("application/vnd.google-apps.folder");
                final DataStore<Serializable> dataStore = getDataStore(String.valueOf(userId));
                if (!dataStore.containsKey("folder")) {
                    File createdFolder = drive.files().create(folder).execute();
                    folderId = createdFolder.getId();
                    dataStore.set("folder", createdFolder.getId());
                } else {
                    folderId = (String) dataStore.get("folder");
                    try {
                        drive.files().get(folderId).execute();
                    } catch (Exception e) {
                        File createdFolder = drive.files().create(folder).execute();
                        folderId = createdFolder.getId();
                        dataStore.set("folder", createdFolder.getId());
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
    public Observable<String> post(InputStream is) {
        return Observable.create(subscriber -> {
            File file = new File();
            file.getParents().add(folderId);
            try {
                String id = drive.files().create(file, new InputStreamContent(null, is))
                        .execute()
                        .getId();
                subscriber.onNext(id);
                subscriber.onCompleted();
            } catch (IOException e) {
                subscriber.onError(e);
            }

        });
    }

    @Override
    public Observable<Boolean> delete(String pid) {
        return Observable.create(subscriber -> {
            try {
                drive.files().delete(pid);
                subscriber.onNext(true);
                subscriber.onCompleted();
            } catch (IOException e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Observable<Boolean> put(String pid, InputStream is) {
        return Observable.create(subscriber -> {
            File file = new File();
            file.setName(pid);
            try {
                drive.files().create(file, new InputStreamContent(null, is))
                        .execute();
                subscriber.onNext(true);
                subscriber.onCompleted();
            } catch (IOException e) {
                subscriber.onError(e);
            }

        });

    }

    @Override
    public Observable<InputStream> get(String pid) {
        return Observable.create(subscriber -> {
            try {
                subscriber.onNext(drive.files().get(pid).executeMediaAsInputStream());
                subscriber.onCompleted();
            } catch (IOException e) {
                subscriber.onError(e);
            }

        });
    }

    @Override
    public String providerId() {
        return null;
    }

    private static GoogleClientSecrets appCredentials() throws IOException {
        return GoogleClientSecrets.load(getDefaultInstance(),
                new InputStreamReader(DriveSample.class.getResourceAsStream("/google.json")));
    }

    @Override
    public <V extends Serializable> DataStore<V> getDataStore(String id) throws IOException {
        return confs.stream()
                .filter(conf -> conf.getId().equals(id))
                .findFirst()
                .orElseGet(() -> {
                    GDriveConf conf = new GDriveConf(id, this);
                    confs.add(conf);
                    return conf;
                });
    }
}
