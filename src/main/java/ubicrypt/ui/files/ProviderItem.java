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

import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

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
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.UbiProvider;

public class ProviderItem implements ITreeItem {
    private final UbiProvider provider;
    private final ImageView imageView;
    private final ContextMenu menu;
    private final ProviderDescriptor descriptor;
    private final HBox hbox;

    public ProviderItem(final UbiProvider provider, final ProviderDescriptor descriptor, final Consumer<UbiProvider> providerRemover) {
        this.provider = provider;
        this.descriptor = descriptor;
        hbox = new HBox();
        Label label = new Label(descriptor.getDescription() + " " + provider.providerId());
        hbox.setAlignment(Pos.CENTER);
        Pane pane = new Pane();
        HBox.setHgrow(pane, Priority.ALWAYS);
        Button removeButton = new Button("", new Glyph("FontAwesome", FontAwesome.Glyph.REMOVE));
        removeButton.setOnMouseClicked(mouseEvent -> {
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
        imageView = new ImageView() {{
            getStyleClass().add(String.format("tree-provider-%s", descriptor.getCode()));
            setFitHeight(40);
            setFitWidth(40);
        }};
        hbox.getChildren().addAll(imageView, label, pane, removeButton);
        menu = new ContextMenu();
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
/*
        return Optional.of(event -> {
            ComboBox comboBox = new ComboBox();
            comboBox.getItems().add("Remove");
            comboBox.show();
        });
*/
    }

    @Override
    public ContextMenu getContextMenu() {
        return menu;
    }

    public UbiProvider getProvider() {
        return provider;
    }
}
