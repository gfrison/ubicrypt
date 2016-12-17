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
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import rx.Observable;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import static org.slf4j.LoggerFactory.getLogger;

public class Anchor {

  private static final Logger log = getLogger(Anchor.class);
  private static final Anchor ctx = new Anchor();
  //FXLoader bug: https://community.oracle.com/message/11240449
  private static final ResourceBundleWrapper bundle =
      new ResourceBundleWrapper(ResourceBundle.getBundle("fx"));
  private static final ConcurrentHashMap<String, Visual> scenes = new ConcurrentHashMap<>();
  private final PublishSubject<char[]> passwordStream = PublishSubject.create();
  private final BufferUntilSubscriber<Object> controllerPublisher = BufferUntilSubscriber.create();
  private final BufferUntilSubscriber<Object> showPublisher = BufferUntilSubscriber.create();
  private final rx.Observable<Object> controllerStream = controllerPublisher.cache();
  private final rx.Observable<Object> showStream = showPublisher.cache();
  private final Stack<Visual> levels = new Stack<>();
  private Stage stage;
  private AtomicReference<Visual> currentVisual = new AtomicReference<>();

  private Anchor() {}

  public static Anchor anchor() {
    return ctx;
  }

  public void browse(final String fxml) {
    browse(fxml, null);
  }

  public <R> void browse(final String fxml, final R data) {
    levels.push(currentVisual.get());
    Visual tupla = showScene(fxml, data);
    tupla.getScene().setUserData(fxml);
  }

  public List<String> getSceneStack() {
    return levels.stream().map(Visual::getFxml).collect(Collectors.toList());
  }

  public Scene show(final String fxml) {
    return showScene(fxml, null).getScene();
  }

  public <R> Scene show(final String fxml, final R data) {
    return showScene(fxml, data).getScene();
  }

  private <R> Visual showScene(final String fxml, final R data) {
    Visual visual = scenes.computeIfAbsent(fxml, f -> loadFrom(fxml));
    if (data != null) {
      final Consumer<R> controller = (Consumer<R>) visual.getController();
      controller.accept(data);
    }
    show(visual);
    return visual;
  }

  private Visual loadFrom(final String fxml) {
    final FXMLLoader loader =
        new FXMLLoader(Anchor.class.getResource(String.format("/fxml/%s.fxml", fxml)), bundle);
    try {
      final Scene scene =
          new Scene(loader.load()) {
            {
              getStylesheets().add("/main.css");
            }
          };

      return new Visual(fxml, scene, loader.getController());
    } catch (final IOException e) {
      Throwables.propagate(e);
    }
    return null;
  }

  public void popScene() {
    popScene(1);
  }

  public void popScene(final int skip) {
    try {
      IntStream.range(0, skip - 1)
          .forEach(
              i -> {
                levels.pop();
              });
      Platform.runLater(() -> show(levels.pop()));
    } catch (final Exception e) {
      log.warn("error on popScene", e);
    }
  }

  private void show(Visual visual) {
    showPublisher.onNext(visual.getController());
    visual
        .getScene()
        .setOnKeyPressed(
            event -> {
              if (event.getCode() == KeyCode.DIGIT7 && event.isControlDown()) {
                anchor().browse("showConfig");
                return;
              }
              if (event.getCode() == KeyCode.L && event.isControlDown()) {
                anchor().browse("log");
                return;
              }
              if (event.getCode() == KeyCode.H && event.isControlDown()) {
                anchor().popHome();
                return;
              }
            });

    stage.setScene(visual.getScene());
    stage.show();
    currentVisual.set(visual);
  }

  public void popHome() {
    popScene(levels.size());
  }

  public PublishSubject<char[]> getPasswordStream() {
    return passwordStream;
  }

  public Subject<Object, Object> getControllerPublisher() {
    return controllerPublisher;
  }

  public Observable<Object> getControllerStream() {
    return controllerStream;
  }

  public BufferUntilSubscriber<Object> getShowPublisher() {
    return showPublisher;
  }

  public Observable<Object> getShowStream() {
    return showStream;
  }

  public Stage getStage() {
    return stage;
  }

  public void setStage(final Stage stage) {
    this.stage = stage;
  }

  public void registerController(final Initializable ctrl) {
    getControllerPublisher().onNext(ctrl);
  }

  public static class ResourceBundleWrapper extends ResourceBundle {

    private final ResourceBundle bundle;

    public ResourceBundleWrapper(final ResourceBundle bundle) {
      this.bundle = bundle;
    }

    @Override
    protected Object handleGetObject(final String key) {
      return bundle.getObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
      return bundle.getKeys();
    }

    @Override
    public boolean containsKey(final String key) {
      return bundle.containsKey(key);
    }

    @Override
    public Locale getLocale() {
      return bundle.getLocale();
    }

    @Override
    public Set<String> keySet() {
      return bundle.keySet();
    }
  }

  static class Visual {
    private final String fxml;
    private final Scene scene;
    private final Object controller;

    public Visual(String fxml, Scene scene, Object controller) {
      this.fxml = fxml;
      this.scene = scene;
      this.controller = controller;
    }

    public String getFxml() {
      return fxml;
    }

    public Scene getScene() {
      return scene;
    }

    public Object getController() {
      return controller;
    }
  }
}
