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

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;

import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.s3.S3Conf;
import ubicrypt.core.provider.s3.S3Provider;
import ubicrypt.ui.StackNavigator;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.ui.Anchor.anchor;

public class S3ProviderController implements Initializable {
  private static final Logger log = getLogger(S3ProviderController.class);
  private final EventHandler<? super KeyEvent> onKeyPressed;
  @FXML
  TextField accessKey;
  @FXML
  TextField secret;
  @FXML
  Label error;
  @FXML
  Button finish;
  @FXML
  Button back;
  @FXML
  Button listBuckets;
  @FXML
  ChoiceBox buckets;
  @FXML
  ChoiceBox regions;
  @Inject
  ProviderCommander providerCommander;
  StackNavigator navigator;

  {
    onKeyPressed =
      event -> {
        if (!checkInputs()) {
          return;
        }
        if (event instanceof KeyEvent
          && event.getCode() == KeyCode.ENTER
          && !listBuckets.isDisabled()) {
          listBuckets();
        }
      };
  }

  private boolean checkInputs() {
    if (isEmpty(accessKey.getText())) {
      disableControls();
      return false;
    }
    if (isEmpty(secret.getText())) {
      disableControls();
      return false;
    }
    if (regions.getSelectionModel().getSelectedItem() == null) {
      disableControls();
      return false;
    }
    listBuckets.setDisable(false);
    return true;
  }

  private void disableControls() {
    finish.setDisable(true);
    buckets.setDisable(true);
    listBuckets.setDisable(true);
  }

  private void clearInputs() {
    accessKey.setText("");
    secret.setVisible(false);
    error.setText("");
    disableControls();
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    anchor().getControllerPublisher().onNext(this);
    accessKey.setOnKeyPressed(onKeyPressed);
    secret.setOnKeyPressed(onKeyPressed);
    regions.setOnMouseClicked(mouseEvent -> checkInputs());
    regions.setOnKeyPressed(onKeyPressed);
    regions.setOnAction(actionEvent -> checkInputs());
    back.setOnMouseClicked(mouseEvent -> navigator.popLayer());
  }

  public void listBuckets() {
    AmazonS3Client client =
      new AmazonS3Client(
        new AWSCredentials() {
          @Override
          public String getAWSAccessKeyId() {
            return accessKey.getText();
          }

          @Override
          public String getAWSSecretKey() {
            return secret.getText();
          }
        });
    try {
      client.listBuckets().forEach(bucket -> buckets.getItems().add(bucket.getName()));
      error.setVisible(false);
      error.setText("");
      buckets.requestFocus();
    } catch (SdkClientException e) {
      log.info("error accessing s3:{}", e.getMessage());
      error.setVisible(true);
      error.setText(e.getMessage());
    }
    buckets.setDisable(false);
    finish.setDisable(false);
    finish.setOnMouseClicked(
      addEvent -> {
        S3Provider provider = new S3Provider();
        S3Conf conf = new S3Conf();
        conf.setAccessKeyId(accessKey.getText());
        conf.setSecrectKey(secret.getText());
        conf.setRegion(Regions.fromName((String) regions.getSelectionModel().getSelectedItem()));
        conf.setBucket((String) buckets.getSelectionModel().getSelectedItem());
        provider.setConf(conf);
        providerCommander
          .register(provider)
          .subscribe(
            result -> {
              log.info("provider s3:{} registered:{}", conf.getBucket(), result);
              clearInputs();
              navigator.popHome();
            },
            err -> {
              log.error("error on adding s3 provider", err);
              Platform.runLater(
                () -> {
                  error.setVisible(true);
                  error.setText("Error: " + err.getMessage());
                });
            });
      });
  }
}
