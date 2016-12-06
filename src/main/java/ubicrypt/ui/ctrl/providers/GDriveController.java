package ubicrypt.ui.ctrl.providers;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import rx.functions.Actions;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.gdrive.GDriveAuthorizer;
import ubicrypt.core.provider.gdrive.GDriveConf;
import ubicrypt.core.provider.gdrive.GDriveProvider;
import ubicrypt.ui.OSUtil;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;

public class GDriveController implements Initializable {
    private static final Logger log = getLogger(GDriveController.class);
    @Inject
    OSUtil osUtil;
    @Inject
    ProviderCommander providerCommander;
    @FXML
    Label error;
    @FXML
    Label message;
    @FXML
    Button back;
    @FXML
    Button gdrive;
    @Inject
    protected GDriveAuthorizer authorizer;
    protected String url;
    protected StackNavigator navigator;

    public void authorize(MouseEvent mouseEvent) {
        osUtil.openUrl(url);
        gdrive.setDisable(true);
        back.setDisable(true);
        try {
            GDriveConf conf = authorizer.credential();
            message.setText("Authorization received...");
            providerCommander.register(new GDriveProvider(conf)).subscribe(Actions.empty(), e -> {
                log.error(e.getMessage(), e);
                error.setText(e.getMessage());
            }, () -> navigator.popHome());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            error.setText(e.getMessage());
        }


    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            url = authorizer.authorizeUrl();
        } catch (GeneralSecurityException e) {
            log.error(e.getMessage(), e);
            error.setText(e.getMessage());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            error.setText(e.getMessage());
        }
    }

    public void setNavigator(StackNavigator navigator) {
        this.navigator = navigator;
    }


    public void back(MouseEvent mouseEvent) {
        if (authorizer.isListening()) {
            authorizer.close();
        }
        navigator.popHome();
    }
}
