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

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.application.HostServices;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import rx.subjects.PublishSubject;
import ubicrypt.core.Utils;
import ubicrypt.ui.Anchor;

import static org.slf4j.LoggerFactory.getLogger;

public class LoginController implements Initializable, Consumer<HostServices> {
  private static final Logger log = getLogger(LoginController.class);
  @FXML Button submit;
  @FXML Label errorLabel;
  @FXML private PasswordField pwd;
  private final EventHandler handler =
      event -> {
        if (event instanceof KeyEvent && ((KeyEvent) event).getCode() != KeyCode.ENTER) {
          return;
        }
        try {
          if (StringUtils.isNotEmpty(pwd.getText())
              && Utils.readPrivateKey(pwd.getText().toCharArray()) != null) {
            log.debug("password match");
            submit.setDisable(true);
            errorLabel.setVisible(false);
            final PublishSubject<char[]> passwordStream = Anchor.anchor().getPasswordStream();
            passwordStream.onNext(pwd.getText().toCharArray());
            passwordStream.onCompleted();
            errorLabel.setVisible(false);
            return;
          }
        } catch (PGPException e) {
          log.info("password mismatch");
        }
        errorLabel.setVisible(true);
      };

  @Override
  public void initialize(final URL location, final ResourceBundle resources) {
    submit.setOnMouseClicked(handler);
    submit.setOnKeyPressed(handler);
    pwd.setOnKeyPressed(handler);
  }

  @Override
  public void accept(HostServices hostServices) {}
}
