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

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import rx.Observable;
import rx.functions.Actions;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.ExportConfig;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.util.PGPKValue;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;

public class ImportConfigCtrl implements Initializable {
  private static final Logger log = getLogger(ImportConfigCtrl.class);
  @Inject
  ProviderCommander providerCommander;
  @Inject
  LocalConfig localConfig;
  @Inject
  PGPService pgpService;
  StackNavigator navigator;
  @FXML
  private TextArea text;
  @FXML
  private Button importConfig;
  @FXML
  private Button cancel;

  @Override
  public void initialize(final URL location, final ResourceBundle resources) {
    cancel.setOnMouseClicked(event -> navigator.popLayer());
    importConfig.setOnMouseClicked(
      event -> {
        try {
          final ExportConfig ret = loadConfig();
          final Observable<Boolean> pksObservable =
            Observable.merge(
              ret.getOwnedPKs()
                .stream()
                .map(PGPKValue::getValue)
                .map(providerCommander::addOwnedPK)
                .collect(Collectors.toList()));
          final Observable<Boolean> proObservable =
            Observable.merge(
              ret.getProviders()
                .stream()
                .map(providerCommander::register)
                .collect(Collectors.toList()));
          pksObservable
            .concatWith(proObservable)
            .doOnNext(next -> Platform.runLater(() -> navigator.popHome()))
            .subscribe(Actions.empty(), err -> log.error(err.getMessage(), err));
        } catch (final IOException e) {
          log.error(e.getMessage(), e);
        }
      });
  }

  private ExportConfig loadConfig() throws IOException {
    final ArmoredInputStream is =
      new ArmoredInputStream(new ByteArrayInputStream(text.getText().getBytes()));
    return Utils.unmarshall(pgpService.decrypt(is), ExportConfig.class);
  }
}
