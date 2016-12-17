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

import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.ExportConfig;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;

public class ExportConfigCtrl implements Initializable {
  private static final Logger log = getLogger(ExportConfigCtrl.class);
  @Inject
  PGPService pgpService;
  @Inject
  LocalConfig localConfig;

  @Autowired
  @Qualifier("keyPair")
  PGPKeyPair keyPair;

  @FXML
  TextArea text;
  @FXML
  Button copy;
  @FXML
  Button cancel;
  StackNavigator navigator;

  @Override
  public void initialize(final URL location, final ResourceBundle resources) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ArmoredOutputStream armor = new ArmoredOutputStream(out);
    try {
      armor.write(
        IOUtils.toByteArray(
          pgpService.encrypt(
            Utils.marshallIs(ExportConfig.copyFrom(localConfig, keyPair.getPublicKey())))));
      armor.close();
    } catch (final IOException e) {
      log.error(e.getMessage(), e);
    }
    text.setText(out.toString());
    copy.setOnMouseClicked(
      event -> {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(out.toString());
        clipboard.setContent(content);
      });
    cancel.setOnMouseClicked(event -> navigator.popLayer());
  }
}
