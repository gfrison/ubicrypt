package ubicrypt.ui.ctrl.providers;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javax.inject.Inject;

import javafx.application.HostServices;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import reactor.fn.tuple.Tuple3;
import rx.functions.Actions;
import ubicrypt.core.Utils;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.provider.gdrive.GDriveAuthorizer;
import ubicrypt.core.provider.gdrive.GDriveConf;
import ubicrypt.core.provider.gdrive.GDriveProvider;
import ubicrypt.ui.StackNavigator;

import static javafx.application.Platform.runLater;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.create;

public class GDriveInfoController implements Initializable, Consumer<Tuple3<GDriveProvider, Consumer<UbiProvider>, ProviderStatus>> {
    private static final Logger log = getLogger(GDriveInfoController.class);
    @Inject
    ProviderLifeCycle providerLifeCycle;
    @FXML
    Label error;
    @FXML
    Label message;
    @FXML
    Label email;
    @FXML
    Label status;
    @FXML
    Label numberFiles;
    @FXML
    Label totalSize;
    @FXML
    Button gdrive;
    @FXML
    Button back;
    private GDriveProvider provider;
    @Inject
    protected GDriveAuthorizer authorizer;
    @Inject
    HostServices hostServices;
    private String url;
    protected StackNavigator navigator;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }


    @Override
    public void accept(Tuple3<GDriveProvider, Consumer<UbiProvider>, ProviderStatus> tuple) {
        this.provider = tuple.getT1();
        status.setText(tuple.getT3().name());
        email.setText(tuple.getT1().getConf().getEmail());
        if (tuple.getT3() == ProviderStatus.unauthorized) {
            gdrive.setVisible(true);
            try {
                url = authorizer.authorizeUrl();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                error.setText(e.getMessage());
                gdrive.setDisable(true);
            }
        } else {
            providerLifeCycle.enabledProviders()
                    .stream()
                    .filter(providerHook -> providerHook.getProvider().equals(tuple.getT1()))
                    .findFirst()
                    .ifPresent(providerHook -> create(providerHook.getAcquirer())
                            .subscribe(acquirerReleaser -> {
                                runLater(() -> numberFiles.setText(String.valueOf(acquirerReleaser.getRemoteConfig().getRemoteFiles().size())));
                                acquirerReleaser.getReleaser().call();
                            }));
            tuple.getT1().availableSpace()
                    .subscribe(space -> runLater(() -> totalSize.setText(byteCountToDisplaySize(space))));
        }

    }

    public void authorize(MouseEvent mouseEvent) {
        hostServices.showDocument(url);
        gdrive.setDisable(true);
        back.setDisable(true);
        try {
            GDriveConf conf = authorizer.credential();
            message.setText("Authorization received...");
            provider.setConf(conf);
            providerLifeCycle.activateProvider(provider).subscribe(Actions.empty(), err -> {
                error.setText(err.getMessage());
                Utils.logError.call(err);
                back.setDisable(false);
            }, () -> navigator.popHome());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            error.setText(e.getMessage());
            back.setDisable(false);
        }
    }

    public void back(MouseEvent mouseEvent) {
        if (authorizer.isListening()) {
            authorizer.close();
        }
        navigator.popHome();
    }

    public void setNavigator(StackNavigator navigator) {
        this.navigator = navigator;
    }
}
