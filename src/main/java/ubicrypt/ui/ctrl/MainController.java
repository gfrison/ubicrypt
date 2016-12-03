package ubicrypt.ui.ctrl;

import org.controlsfx.control.StatusBar;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Resource;
import javax.inject.Inject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import rx.Observable;
import rx.subjects.PublishSubject;
import ubicrypt.UbiCrypt;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.ui.ctrl.providers.ProvidersController;
import ubicrypt.ui.files.FilesController;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.slf4j.LoggerFactory.getLogger;

public class MainController implements Initializable {
    private static final Logger log = getLogger(MainController.class);
    @Inject
    PGPService pgpService;
    @Resource
    PublishSubject<ProgressFile> progressEvents;
    @Inject
    @Qualifier("appEvents")
    private Observable<Object> appEvents;
    @Inject
    Stage stage;
    @FXML
    StatusBar footer;
    @FXML
    FilesController filesController;
    @FXML
    ProvidersController providersController;
    @FXML
    TabPane tabs;

    private String fxml;
    private final Set<ProgressFile> filesInProgress = ConcurrentHashMap.newKeySet();


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        fxml = substringBefore(substringAfterLast(url.getFile(), "/"), ".fxml");
        log.debug("initialize fxml:{}, pgpservice:{}", fxml, pgpService);
        //file progress monitor
        progressEvents.subscribe(progress -> Platform.runLater(() -> {
            if (progress.isCompleted() || progress.isError()) {
                log.debug("progress completed");
                if (!filesInProgress.remove(progress)) {
                    log.warn("progress not tracked. progress file:{}, element:{}", progress.getProvenience().getFile());
                }
                Timeline timeline = new Timeline(new KeyFrame(
                        Duration.seconds(2),
                        ae -> {
                            footer.setText("");
                            footer.setProgress(0D);
                        }));
                timeline.play();
            } else {
                filesInProgress.add(progress);
            }
            if (filesInProgress.isEmpty()) {
                return;
            }
            footer.setVisible(true);
            filesInProgress.stream().findFirst()
                    .ifPresent(pr -> {
                        footer.setText(abbreviate(pr.getProvenience().getFile().getPath().getFileName().toString(), 30)
                                + " → " + abbreviate(pr.getTarget().toString(), 30));
                        footer.setProgress((double) progress.getChunk() / pr.getProvenience().getFile().getSize());
                    });
        }));

    }

    public void about(ActionEvent actionEvent) {
        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("UbiCrypt");
        about.setHeaderText("Version: " + UbiCrypt.getVersion());
        about.setContentText("Author: Giancarlo Frison<giancarlo@gfrison.com>");
        about.initOwner(stage);
        about.showAndWait();
    }

    public void addFiles(ActionEvent actionEvent) {
        filesController.addFile(null);
    }

    public void exit(ActionEvent actionEvent) {
        Platform.exit();
    }

    public void addFolder(ActionEvent actionEvent) {
        filesController.addFolder();
    }

    public void addFileProvider(ActionEvent actionEvent) {
        tabs.getSelectionModel().select(1);
        providersController.getNavigator().browse("provider/file", "file");
    }

    public void addS3Provider(ActionEvent actionEvent) {
        tabs.getSelectionModel().select(1);
        providersController.getNavigator().browse("provider/s3", "s3");
    }

    public void addGdriveProvider(ActionEvent actionEvent) {
        tabs.getSelectionModel().select(1);
        providersController.getNavigator().browse("provider/gdrive", "gdrive");
    }
}
