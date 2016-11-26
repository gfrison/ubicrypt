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

import java.util.Optional;

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import ubicrypt.ui.Anchor;

public class RootProvidersItem implements ITreeItem {
    private final ContextMenu menu;
    private final Anchor ctx = Anchor.anchor();
    private final ImageView image;

    public RootProvidersItem() {
        menu = new ContextMenu();
        final MenuItem remove = new MenuItem("Add Cloud Storage");
        remove.setOnAction(event -> ctx.browse("selectProvider"));
        menu.getItems().add(remove);
        image = new ImageView() {{
            getStyleClass().add("tree-providers");
            setFitHeight(20);
            setFitWidth(20);
        }};
    }

    @Override
    public Node getGraphics() {
        return image;
    }

    @Override
    public String getLabel() {
        return "Cloud Storages";
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
}
