/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.ui.ctrl;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javafx.application.HostServices;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import rx.subjects.PublishSubject;
import ubicrypt.ui.Anchor;

import static org.slf4j.LoggerFactory.getLogger;

public class CreateKeyController implements Initializable, Consumer<HostServices> {
    private static final Logger log = getLogger(CreateKeyController.class);
    @FXML
    PasswordField pwd;
    @FXML
    PasswordField pwd2;
    @FXML
    Label errorLabel;
    @FXML
    Button submit;
    private final EventHandler handler = event -> {
        if (event instanceof KeyEvent && ((KeyEvent) event).getCode() != KeyCode.ENTER) {
            return;
        }
        if (StringUtils.isNotEmpty(pwd.getText()) && StringUtils.isNotEmpty(pwd2.getText()) && pwd.getText().equals(pwd2.getText())) {
            log.debug("password match");
            errorLabel.setVisible(false);
            final PublishSubject<char[]> passwordStream = Anchor.anchor().getPasswordStream();
            passwordStream.onNext(pwd.getText().toCharArray());
            passwordStream.onCompleted();
            errorLabel.setVisible(false);
            submit.setDisable(true);
            return;
        }
        errorLabel.setVisible(true);

    };
    private HostServices hostServices;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        submit.onMouseClickedProperty().setValue(handler);
        pwd.setOnKeyPressed(handler);
        pwd2.setOnKeyPressed(handler);
    }

    public void linkLicense(ActionEvent actionEvent) {
        hostServices.showDocument("https://github.com/gfrison/ubicrypt/blob/master/LICENSE.md");
    }

    @Override
    public void accept(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    public void agree(ActionEvent event) {
        CheckBox chk = (CheckBox) event.getSource();
        if (chk.isSelected()) {
            submit.setDisable(false);
        } else {
            submit.setDisable(true);
        }
    }
}
