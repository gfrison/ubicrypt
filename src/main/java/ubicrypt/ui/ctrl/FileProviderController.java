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

import org.slf4j.Logger;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.file.FileConf;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;

public class FileProviderController implements Initializable {
  private static final Logger log = getLogger(FileProviderController.class);

  @Inject ProviderCommander providerCommander;
  @Inject Stage stage;
  @FXML Button selectFolder;
  @FXML Button finish;
  @FXML TextField textFolder;
  @FXML Button back;
  StackNavigator navigator;

  @Override
  public void initialize(final URL location, final ResourceBundle resources) {
    textFolder.setText(null);
    finish.setDisable(true);
    selectFolder.setOnMouseClicked(
        event -> {
          final DirectoryChooser fc = new DirectoryChooser();
          final File dir = fc.showDialog(stage);
          if (dir != null) {
            textFolder.setText(dir.toString());
            finish.setDisable(false);
          }
        });
    back.setOnMouseClicked(mouseEvent -> navigator.popLayer());
    finish.setOnMouseClicked(
        mouseEvent -> {
          final FileProvider provider = new FileProvider();
          provider.setConf(new FileConf(Paths.get(textFolder.getText())));
          providerCommander
              .register(provider)
              .filter(Boolean::booleanValue)
              .subscribe(
                  res -> log.info("new provider:{}, add result:{}", provider, res),
                  err -> log.error(err.getMessage(), err));
          navigator.popHome();
        });
  }
}
