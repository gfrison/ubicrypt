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

import org.apache.commons.lang3.time.DateFormatUtils;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.ui.Anchor.anchor;

public class ConfirmPKController implements Initializable, Consumer<PGPPublicKey> {
    private static final Logger log = getLogger(ConfirmPKController.class);
    @FXML
    Label creationDate;
    @FXML
    Label algorithm;
    @FXML
    ListView<String> userIds;
    @Inject
    PGPService pgpService;
    @Inject
    ProviderCommander providerCommander;
    @FXML
    Button cancel;
    @FXML
    Button add;
    StackNavigator navigator;

    @Override
    public void accept(final PGPPublicKey pgpPublicKey) {
        log.debug("received pk:{}", pgpPublicKey);
        creationDate.setText(DateFormatUtils.format(pgpPublicKey.getCreationTime(), "yyyy-MM-dd HH:mm"));
        algorithm.setText(PGPEC.algorithm(pgpPublicKey.getAlgorithm()));
        Utils.toStream(pgpPublicKey.getRawUserIDs()).forEach(userId -> userIds.getItems().add((String) userId));
        cancel.setOnMouseClicked(event -> anchor().popScene());
        add.setOnMouseClicked(event -> providerCommander.addOwnedPK(pgpPublicKey)
                .doOnError(err -> log.error(err.getMessage(), err))
                .doOnCompleted(() -> Platform.runLater(() -> navigator.browse("exportConfig")))
                .subscribe());
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {

    }
}
