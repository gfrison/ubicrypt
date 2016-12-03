package ubicrypt.core.provider.gdrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequestInitializer;
import com.google.api.services.drive.DriveScopes;
import com.google.common.base.Throwables;

import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import rx.subjects.Subject;
import ubicrypt.core.IStoppable;
import ubicrypt.core.events.ShutdownRegistration;

import static com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance;
import static rx.Observable.create;
import static ubicrypt.core.provider.gdrive.GDriveProvider.credentialId;

public class GDriveAuthorizer implements DataStoreFactory, IStoppable {

    @Resource
    @Qualifier("appEvents")
    private Subject appEvents;
    private GDriveConf conf;
    private NetHttpTransport http;
    private GoogleAuthorizationCodeFlow flow;
    private LocalServerReceiver receiver;
    private String redirectUri;
    private final AtomicBoolean status = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        appEvents.onNext(new ShutdownRegistration(this));
    }

    public String authorizeUrl() throws GeneralSecurityException, IOException {
        if (!status.compareAndSet(false, true)) {
            throw new IllegalStateException("authorization in progress");
        }
        try {
            conf = new GDriveConf(credentialId, this);
            http = GoogleNetHttpTransport.newTrustedTransport();
            flow = new GoogleAuthorizationCodeFlow.Builder(
                    http, getDefaultInstance(), appCredentials(), Arrays.asList(DriveScopes.DRIVE_FILE))
                    .setDataStoreFactory(this)
                    .setAccessType("offline")
                    .build();
            receiver = new LocalServerReceiver();
            redirectUri = receiver.getRedirectUri();
            return flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
        } catch (Exception e) {
            close();
            status.set(false);
            Throwables.propagate(e);
            return null;
        }
    }

    public GDriveConf credential() throws IOException {
        if (!status.get()) {
            throw new IllegalStateException("authorization not requested");
        }
        try {
            String code = receiver.waitForCode();
            receiver.stop();
            TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            Credential credential = flow.createAndStoreCredential(response, credentialId);
            Drive drive = new Drive.Builder(http, getDefaultInstance(), credential)
                    .setDriveRequestInitializer(new DriveRequestInitializer())
                    .setApplicationName("UbiCrypt").build();
            String email = drive.about().get().setFields("user, storageQuota").execute().getUser().getEmailAddress();
            conf.setEmail(email);
            return conf;
        } catch (Exception e) {
            close();
            Throwables.propagate(e);
            return null;
        } finally {
            status.set(false);
        }
    }

    public void close() {
        if (receiver != null) {
            try {
                receiver.stop();
            } catch (IOException e) {

            }
            status.set(false);
        }
    }

    private static GoogleClientSecrets appCredentials() throws IOException {
        return GoogleClientSecrets.load(getDefaultInstance(),
                new InputStreamReader(GDriveAuthorizer.class.getResourceAsStream("/google.json")));
    }

    @Override
    public <V extends Serializable> DataStore<V> getDataStore(String id) throws IOException {
        return conf;
    }

    @Override
    public Observable<Void> stop() {
        return create(subscriber -> {
            if (receiver != null) {
                close();
            }
            subscriber.onCompleted();
        });
    }

    public boolean isListening() {
        return status.get();
    }
}
