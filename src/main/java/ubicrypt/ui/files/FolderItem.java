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
package ubicrypt.ui.files;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.nio.file.Path;
import java.util.Optional;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

import static com.google.common.base.Preconditions.checkArgument;

public class FolderItem implements ITreeItem {
  final ImageView imageView =
      new ImageView() {
        {
          getStyleClass().add("tree-folder");
          setFitHeight(20);
          setFitWidth(20);
        }
      };
  private final Path path;
  private final String label;
  private final ContextMenu menu = new ContextMenu();

  public FolderItem(final Path path) {
    checkArgument(path != null, "path must not be null");
    this.path = path;
    label = null;
  }

  public FolderItem(
      final Path path,
      final EventHandler<ActionEvent> onAddHandler,
      final EventHandler<ActionEvent> onAddFolder) {
    checkArgument(path != null, "path must not be null");
    this.path = path;
    label = null;
    final MenuItem add = new MenuItem("Add Files");
    add.setOnAction(onAddHandler);
    final MenuItem addFolder = new MenuItem("Add Folder");
    addFolder.setOnAction(onAddFolder);
    menu.getItems().addAll(add, addFolder);
  }

  public FolderItem(final String label, final Path path) {
    this.label = label;
    this.path = path;
  }

  public boolean isFile() {
    return false;
  }

  public Path getPath() {
    return path;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    final FolderItem folderItem = (FolderItem) o;

    return new EqualsBuilder().append(path, folderItem.path).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(path).toHashCode();
  }

  @Override
  public String toString() {
    return (label != null) ? label : path.getFileName().toString();
  }

  @Override
  public Node getGraphics() {
    return imageView;
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
}
