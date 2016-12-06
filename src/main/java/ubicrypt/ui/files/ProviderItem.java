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
package ubicrypt.ui.files;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import reactor.fn.tuple.Tuple;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.ui.StackNavigator;

import static org.slf4j.LoggerFactory.getLogger;

public class ProviderItem implements ITreeItem {
    private static final Logger log = getLogger(ProviderItem.class);
    private final UbiProvider provider;
    private final ImageView providerIcon;
    private final ContextMenu menu;
    private final ProviderDescriptor descriptor;
    private final HBox hbox;
    private final ImageView statusIcon;
    private ProviderStatus status = ProviderStatus.uninitialized;

    public ProviderItem(final UbiProvider provider, final ProviderDescriptor descriptor, final Consumer<UbiProvider> providerRemover, StackNavigator navigator) {
        this.provider = provider;
        this.descriptor = descriptor;
        hbox = new HBox();
        Label description = new Label(descriptor.getDescription());
        description.setFont(Font.font(null, FontWeight.NORMAL, 13));
        Label pid = new Label(provider.providerId());
        pid.setFont(Font.font(null, FontWeight.THIN, 9));
        hbox.setAlignment(Pos.CENTER);
        Pane pane = new Pane();
        HBox.setHgrow(pane, Priority.ALWAYS);
        final ImageView infoGraphic = new ImageView(new Image("/images/info.png"));
        infoGraphic.setPickOnBounds(true);
        infoGraphic.setPreserveRatio(true);
        infoGraphic.setFitWidth(20);
        Button info = new Button();
        info.setGraphic(infoGraphic);
        info.setOnMouseClicked(event -> {
            navigator.browse("provider/" + provider.code() + "-info", Tuple.of(provider, providerRemover, status));
        });
        if (ProviderItem.class.getResource("/fxml/provider/" + provider.code() + "-info.fxml") == null) {
            info.setDisable(true);
        }

        providerIcon = new ImageView() {{
            setImage(descriptor.getLogo().getImage());
            setFitHeight(40);
            setFitWidth(40);
        }};
        statusIcon = new ImageView(new Image("/images/clock.png"));
        statusIcon.setFitWidth(15);
        statusIcon.setFitHeight(15);
        hbox.getChildren().addAll(statusIcon, providerIcon, description, pane, pid, info);
        menu = new ContextMenu();
        final MenuItem remove = new MenuItem("Remove Provider");
        remove.setOnAction(event -> {
            final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Cloud Storage remove");
            alert.setHeaderText(String.format("Storage %s will be removed", provider.providerId()));
            alert.setContentText("Are you sure?");

            final Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK) {
                providerRemover.accept(provider);
            } else {
                // ... user chose CANCEL or closed the dialog
            }
        });
        menu.getItems().addAll(remove);
    }

    @Override
    public String toString() {
        return descriptor.getDescription() + " " + provider.providerId();
    }

    @Override
    public Node getGraphics() {
        return hbox;
    }

    @Override
    public String getLabel() {
        return toString();
    }

    @Override
    public Optional<EventHandler<? super MouseEvent>> onMousePrimaryClick() {
        return Optional.empty();
    }

    @Override
    public Optional<EventHandler<? super MouseEvent>> onMouseSecondaryClick() {
        return Optional.empty();
    }

    @Override
    public ContextMenu getContextMenu() {
        return menu;
    }

    public UbiProvider getProvider() {
        return provider;
    }

    public void changeStatus(ProviderStatus status) {
        log.debug("status:{}, provider:{}", status, provider);
        this.status = status;
        switch (status) {
            case initialized:
            case active:
                statusIcon.setImage(new Image("/images/ok.png"));
                break;
            case uninitialized:
            case unavailable:
            case unauthorized:
            case error:
                statusIcon.setImage(new Image("/images/error.png"));
                break;
            case expired:
                statusIcon.setImage(new Image("/images/sleep.png"));
                break;
            default:
                statusIcon.setImage(new Image("/images/question.png"));

        }
    }
}
