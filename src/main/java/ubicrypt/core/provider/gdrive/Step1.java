package ubicrypt.core.provider.gdrive;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.services.drive.DriveScopes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.inject.Inject;

import static com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance;

public class Step1 implements DataStoreFactory {

    @Inject
    private int deviceId;

    private GDriveConf conf;
    private NetHttpTransport http;
    private GoogleAuthorizationCodeFlow flow;
    private LocalServerReceiver receiver;
    private String redirectUri;

    public Step1() {
    }

    public Step1(int deviceId) {
        this.deviceId = deviceId;
    }

    public String authorizeUrl() throws GeneralSecurityException, IOException {
        http = GoogleNetHttpTransport.newTrustedTransport();
        flow = new GoogleAuthorizationCodeFlow.Builder(
                http, getDefaultInstance(), appCredentials(), Arrays.asList(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(this)
                .setAccessType("offline")
                .build();
        receiver = new LocalServerReceiver();
        redirectUri = receiver.getRedirectUri();
        return flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
    }

    public GDriveConf credential() throws IOException {
        // receive authorization code and exchange it for an access token
        String code = receiver.waitForCode();
        TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
        // store credential and return it
        flow.createAndStoreCredential(response, String.valueOf(deviceId));
        return conf;
    }

    private static GoogleClientSecrets appCredentials() throws IOException {
        return GoogleClientSecrets.load(getDefaultInstance(),
                new InputStreamReader(DriveSample.class.getResourceAsStream("/google.json")));
    }

    @Override
    public <V extends Serializable> DataStore<V> getDataStore(String id) throws IOException {
        conf = new GDriveConf(id, this);
        return conf;
    }

}
