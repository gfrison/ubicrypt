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
package ubicrypt.ui.ctrl;

import org.controlsfx.control.StatusBar;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URL;
import java.nio.file.Path;
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
  private final Set<ProgressFile> filesInProgress = ConcurrentHashMap.newKeySet();
  @Inject
  PGPService pgpService;
  @Resource
  PublishSubject<ProgressFile> progressEvents;
  @Inject
  Stage stage;
  @Inject
  Path basePath;
  @FXML
  StatusBar footer;
  @FXML
  FilesController filesController;
  @FXML
  ProvidersController providersController;
  @FXML
  TabPane tabs;

  @Inject
  @Qualifier("appEvents")
  private Observable<Object> appEvents;

  private String fxml;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    fxml = substringBefore(substringAfterLast(url.getFile(), "/"), ".fxml");
    log.debug("initialize fxml:{}, pgpservice:{}", fxml, pgpService);
    //file progress monitor
    progressEvents.subscribe(
      progress ->
        Platform.runLater(
          () -> {
            if (progress.isCompleted() || progress.isError()) {
              log.debug("progress completed");
              if (!filesInProgress.remove(progress)) {
                log.warn(
                  "progress not tracked. progress file:{}, element:{}",
                  progress.getProvenience().getFile());
              }
              Timeline timeline =
                new Timeline(
                  new KeyFrame(
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
            filesInProgress
              .stream()
              .findFirst()
              .ifPresent(
                pr -> {
                  String file =
                    abbreviate(
                      pr.getProvenience()
                        .getFile()
                        .getPath()
                        .getFileName()
                        .toString(),
                      30);
                  String target =
                    abbreviate(
                      pr.getDirection() == ProgressFile.Direction.inbound
                        ? pr.getProvenience().getOrigin().toString()
                        : pr.getTarget().toString(),
                      30);
                  String text = file + " → " + target;
                  if (pr.getDirection() == ProgressFile.Direction.inbound) {
                    text = target + " → " + file;
                  }
                  footer.setText(text);
                  footer.setProgress(
                    (double) progress.getChunk()
                      / pr.getProvenience().getFile().getSize());
                });
          }));
  }

  public void about(ActionEvent actionEvent) {
    Alert about = new Alert(Alert.AlertType.INFORMATION);
    about.setTitle("UbiCrypt");
    about.setHeaderText("Version: " + UbiCrypt.getVersion());
    about.setContentText("Author: Giancarlo Frison <giancarlo@gfrison.com>");
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
