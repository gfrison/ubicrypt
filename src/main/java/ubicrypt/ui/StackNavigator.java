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
package ubicrypt.ui;

import com.google.common.base.Throwables;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.IntStream.range;
import static org.apache.commons.lang3.reflect.FieldUtils.getAllFields;
import static org.apache.commons.lang3.reflect.FieldUtils.writeField;
import static org.slf4j.LoggerFactory.getLogger;

public class StackNavigator {
  private static final Logger log = getLogger(StackNavigator.class);
  //FXLoader bug: https://community.oracle.com/message/11240449
  private static final ResourceBundleWrapper bundle =
      new ResourceBundleWrapper(ResourceBundle.getBundle("fx"));
  private final Pane root;
  private final ControllerFactory controllerFactory;
  private final Stack<String> levels = new Stack<>();

  public StackNavigator(Pane root, CharSequence fxml, ControllerFactory controllerFactory) {
    this.root = root;
    levels.push(fxml.toString());
    this.controllerFactory = controllerFactory;
  }

  public Parent open() {
    return loadFrom(Optional.empty());
  }

  public Parent browse(final String fxml) {
    return browse(fxml, Optional.empty());
  }

  public <R> Parent browse(final String fxml, final R data) {
    levels.push(fxml);
    return loadFrom(Optional.of(data));
  }

  public void popHome() {
    popLayer(levels.size() - 1);
  }

  public void popLayer() {
    popLayer(1);
  }

  public void popLayer(final int skip) {
    try {
      range(0, skip).forEach(i -> levels.pop());
      Platform.runLater(() -> loadFrom(Optional.empty()));
    } catch (final Exception e) {
      log.warn("error on popLayer", e);
    }
  }

  public <R> Parent loadFrom(final Optional<R> data) {
    log.debug("fxml:{}", levels.peek());
    final FXMLLoader loader =
        new FXMLLoader(
            StackNavigator.class.getResource(format("/fxml/%s.fxml", levels.peek())), bundle);
    loader.setControllerFactory(controllerFactory);
    try {
      Parent parent;
      if (root != null) {
        root.getChildren().setAll((Node) loader.load());
        parent = (Parent) root.getChildren().get(0);
      } else {
        parent = loader.load();
      }
      Object controller = loader.getController();
      stream(getAllFields(controller.getClass()))
          .filter(field -> field.getType() == StackNavigator.class)
          .forEach(
              field -> {
                try {
                  writeField(field, controller, this, true);
                } catch (IllegalAccessException e) {
                  log.error("error setting field:{} in:{}", field, controller);
                  Throwables.propagate(e);
                }
                log.debug("{} inject stack navigator", controller.getClass().getSimpleName());
              });
      if (Consumer.class.isAssignableFrom(controller.getClass())) {
        data.ifPresent(((Consumer<R>) controller)::accept);
      }
      return parent;
    } catch (final IOException e) {
      Throwables.propagate(e);
    }
    return null;
  }
}
