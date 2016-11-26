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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.collections.ListChangeListener;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import rx.Observable;
import rx.functions.Actions;
import ubicrypt.core.dto.UbiFile;

import static javafx.application.Platform.runLater;
import static org.slf4j.LoggerFactory.getLogger;

public class TreeCellFactory extends TreeCell<ITreeItem> implements ListChangeListener<TreeItem<ITreeItem>> {
    private static final Logger log = getLogger(TreeCellFactory.class);
    private final TreeView<ITreeItem> treeView;
    private final CopyOnWriteArrayList<TreeItem<ITreeItem>> selected = new CopyOnWriteArrayList<>();
    private final ContextMenu menu;
    private final MenuItem remove;

    public TreeCellFactory(TreeView<ITreeItem> treeView1, Function<UbiFile, Observable<Boolean>> fileUntracker, Observable<Object> appEvents) {
        treeView = treeView1;
        treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        //multi selection listener for tree view
        treeView.getSelectionModel().getSelectedItems().addListener(this);
        menu = new ContextMenu();
        remove = new MenuItem("Untrack File");
        remove.setOnAction(eventMenu -> Observable.merge(selected.stream()
                .map(it -> {
                    if (it.getValue() instanceof FileItem) {
                        return fileUntracker.apply(((FileItem) it.getValue()).getFile());
                    }
                    return Observable.empty();
                })
                .collect(Collectors.toList()))
                .doOnSubscribe(() -> {
                    runLater(() -> remove.setDisable(true));
                })
                .subscribe(Actions.empty(), err -> {
                    runLater(() -> remove.setDisable(false));
                    log.error(err.getMessage(), err);
                }, () -> {
                    runLater(() -> remove.setDisable(false));
                }));
        menu.getItems().add(remove);

    }

    @Override
    protected void updateItem(final ITreeItem item, final boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setOnMouseClicked(null);
        } else {
            setText(getItem() == null ? "" : getItem().getLabel());
            setGraphic(item.getGraphics());
/*
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    log.debug("right click:{}", event);
//                    item.onMouseSecondaryClick().ifPresent(eventHandler -> eventHandler.handle(event));
                    return;
                }
//                item.onMousePrimaryClick().ifPresent(eventHandler -> eventHandler.handle(event));


            });
*/
            if (item instanceof FileItem) {
                setContextMenu(menu);
                return;
            }
            setContextMenu(item.getContextMenu());
        }
    }

    @Override
    public void onChanged(Change<? extends TreeItem<ITreeItem>> change) {
        while (change.next()) {
            selected.clear();
            selected.addAll(change.getList());
/*
            selected.removeAll(change.getRemoved());
            if (change.wasAdded() && change.getFrom() >= 0 && change.getTo() <= change.getList().size() && change.getFrom() <= change.getTo()) {
                selected.addAll(change.getAddedSubList());
            }
*/
            if (selected.size() != change.getList().size()) {
                log.warn("select wrong size:{} instead of:{}", selected.size(), change.getList().size());
            }
        }
    }
}
