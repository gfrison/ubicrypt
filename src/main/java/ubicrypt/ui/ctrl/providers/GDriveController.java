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
  protected GDriveAuthorizer authorizer;
  protected String url;
  protected StackNavigator navigator;
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

  public void authorize(MouseEvent mouseEvent) {
    osUtil.openUrl(url);
    gdrive.setDisable(true);
    back.setDisable(true);
    try {
      GDriveConf conf = authorizer.credential();
      message.setText("Authorization received...");
      providerCommander
        .register(new GDriveProvider(conf))
        .subscribe(
          Actions.empty(),
          e -> {
            log.error(e.getMessage(), e);
            error.setText(e.getMessage());
          },
          () -> navigator.popHome());
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
