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

import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import rx.Observable;
import ubicrypt.ui.StackNavigator;

import static ubicrypt.ui.Anchor.anchor;

public class LogCtrl implements Initializable {

  StackNavigator navigator;
  @FXML private TextArea text;
  @FXML private Button back;

  @Resource
  @Qualifier("systemOut")
  private Observable<String> logStream;

  private AtomicBoolean init = new AtomicBoolean(false);

  @PostConstruct
  public void init() {
    if (init.compareAndSet(false, true)) {
      logStream.subscribe(i -> Platform.runLater(() -> text.appendText(i)));
    }
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    anchor().registerController(this);
    back.setOnMouseClicked(mouseEvent -> navigator.popLayer());
  }
}
