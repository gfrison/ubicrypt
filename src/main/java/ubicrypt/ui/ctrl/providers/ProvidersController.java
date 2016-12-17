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
package ubicrypt.ui.ctrl.providers;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import javax.annotation.Resource;
import javax.inject.Inject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import rx.Observable;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.ProviderEvent;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.ui.ControllerFactory;
import ubicrypt.ui.StackNavigator;
import ubicrypt.ui.files.ProviderItem;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.slf4j.LoggerFactory.getLogger;

public class ProvidersController implements Initializable {
  private static final Logger log = getLogger(ProvidersController.class);
  @FXML VBox root;
  @FXML ListView<ProviderItem> providers;
  @FXML ToolBar availableProviders;
  @Resource List<ProviderDescriptor> providerDescriptors;

  @Resource
  @Qualifier("providerEvent")
  Observable<ProviderEvent> providerEvent;

  @Inject private ControllerFactory controllerFactory;
  @Inject private ProviderCommander providerCommander;

  private final Consumer<UbiProvider> providerRemover =
      provider ->
          providerCommander
              .remove(provider)
              .subscribe(
                  res -> {
                    log.info("provider:{}, removal result:{}", provider, res);
                    if (res) {
                      providers
                          .getItems()
                          .stream()
                          .filter(ti -> ti.getProvider().equals(provider))
                          .findFirst()
                          .ifPresent(providers.getItems()::remove);
                    }
                  },
                  err -> log.error("error on removing provider:{}", provider, err));
  private StackNavigator navigator;

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    String fxml = substringBefore(substringAfterLast(url.getFile(), "/"), ".fxml");
    navigator = new StackNavigator(root, fxml, controllerFactory);
    providers.setCellFactory(
        listView ->
            new ListCell<ProviderItem>() {
              @Override
              protected void updateItem(ProviderItem pi, boolean empty) {
                super.updateItem(pi, empty);
                if (empty) {
                  setText(null);
                  setGraphic(null);
                  return;
                }
                setContextMenu(pi.getContextMenu());
                Platform.runLater(() -> setGraphic(pi.getGraphics()));
              }
            });
    providerDescriptors
        .stream()
        .forEach(
            pd -> {
              Button button = new Button();
              Image img = pd.getLogo().getImage();
              ImageView view = new ImageView(img);
              view.setFitWidth(30.0);
              view.setPickOnBounds(true);
              view.setPreserveRatio(true);
              button.setGraphic(view);
              button.setOnMouseClicked(
                  mouseEvent -> {
                    log.debug("adding provider :{}", pd.getCode());
                    navigator.browse(format("provider/%s", pd.getCode()));
                  });
              button.setTooltip(new Tooltip("Add " + pd.getDescription()));
              availableProviders.getItems().add(button);
            });

    //provider status events
    providerEvent.subscribe(
        pevent -> {
          UbiProvider provider = pevent.getHook().getProvider();
          if (!providers
              .getItems()
              .stream()
              .filter(pi -> pi.getProvider().equals(provider))
              .findFirst()
              .isPresent()) {
            log.info("add new provider:{}", pevent.getHook().getProvider());
            String code =
                providerDescriptors
                    .stream()
                    .filter(pd -> pd.getType() == provider.getClass())
                    .map(ProviderDescriptor::getCode)
                    .findFirst()
                    .get();
            final ProviderItem providerItem =
                new ProviderItem(
                    provider,
                    providerDescriptors
                        .stream()
                        .filter(pd -> pd.getType() == provider.getClass())
                        .findFirst()
                        .get(),
                    providerRemover,
                    navigator);
            providers.getItems().add(providerItem);
            pevent.getHook().getStatusEvents().subscribe(providerItem::changeStatus);
          }
          switch (pevent.getEvent()) {
            case removed:
              //TODO: remove provider
              break;
            default:
              log.warn("unmanaged event:{}", pevent.getEvent());
          }
          providers
              .getItems()
              .stream()
              .filter(pi -> pi.getProvider().equals(provider))
              .findFirst()
              .ifPresent(pi -> pi.changeStatus(pevent.getEvent()));
        });
  }

  public StackNavigator getNavigator() {
    return navigator;
  }
}
