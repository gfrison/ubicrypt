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

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ResourceBundle;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import rx.schedulers.Schedulers;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ftp.FTPConf;
import ubicrypt.core.provider.ftp.FTProvider;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.ui.Anchor.anchor;

public class FTProviderController implements Initializable {
    private static final Logger log = getLogger(FTProviderController.class);
    @FXML
    TextField host;
    @FXML
    TextField port;
    @FXML
    CheckBox anonymous;
    @FXML
    TextField username;
    @FXML
    TextField password;
    @FXML
    Button add;
    @FXML
    Label error;
    @FXML
    TextField folder;
    @Inject
    ProviderCommander providerCommander;
    private final EventHandler<? super KeyEvent> onKeyPressed = event -> {
        if (isEmpty(host.getText())) {
            add.setDisable(true);
            return;
        }
        if (!NumberUtils.isNumber(port.getText())) {
            add.setDisable(true);
            return;
        }
        if (anonymous.isSelected()) {
            username.setDisable(true);
            password.setDisable(true);
        } else {
            if (isEmpty(username.getText())) {
                add.setDisable(true);
                return;
            }
            if (isEmpty(password.getText())) {
                add.setDisable(true);
                return;
            }
        }
        if (isEmpty(host.getText())) {
            add.setDisable(true);
            return;
        }
        add.setDisable(false);
        if (event instanceof KeyEvent && event.getCode() == KeyCode.ENTER && !add.isDisabled()) {
            register();
        }

    };

    @PostConstruct
    public void init() {
        host.setOnKeyPressed(onKeyPressed);
        port.setOnKeyPressed(onKeyPressed);
        anonymous.setOnKeyPressed(onKeyPressed);
        username.setOnKeyPressed(onKeyPressed);
        password.setOnKeyPressed(onKeyPressed);
        anonymous.setOnMouseClicked(event -> {
            if (anonymous.isSelected()) {
                username.setDisable(false);
                password.setDisable(false);
                return;
            }
            username.setDisable(false);
            password.setDisable(false);
        });
        add.setOnMouseClicked(event -> {
            register();
        });
    }

    public void register() {
        try {
            InetAddress.getByName(host.getText());
        } catch (UnknownHostException e) {
            error.setText("Set a proper host");
            error.setVisible(true);
            host.requestFocus();
            return;
        }
        error.setText("");
        final FTProvider ftp = new FTProvider();
        final FTPConf conf = new FTPConf();
        conf.setHost(host.getText());
        conf.setPort(Integer.valueOf(port.getText()));
        conf.setAnonymous(anonymous.isSelected());
        conf.setFolder(folder.getText());
        if (!anonymous.isSelected()) {
            conf.setUsername(username.getText());
            conf.setPassword(password.getText().toCharArray());
        }
        ftp.setConf(conf);

        anchor().browse("wait", "Registering FTP provider");
        try {
            providerCommander.register(ftp).subscribeOn(Schedulers.io()).subscribe(result -> {
                        log.info("provider ftp:{} registered:{}", conf.getHost(), result);
                        clearInputs();
                        anchor().popHome();
                    },
                    err -> {
                        log.error("error on adding ftp provider", err);
                        anchor().popScene();
                        error.setVisible(true);
                        error.setText("Error: " + err.getMessage());
                    });
        } catch (Exception e) {
            log.error("exp", e);
        }
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        add.setDisable(true);
        anchor().getControllerPublisher().onNext(this);
    }

    private void clearInputs() {
        error.setText("");
        error.setVisible(false);
        host.setText("ftp.example.com");
        port.setText("21");
        anonymous.setSelected(false);
        username.setText("");
        password.setText("");
    }
}
