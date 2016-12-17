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

import com.google.common.base.Throwables;

import org.slf4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import rx.Observable;
import ubicrypt.core.FileFacade;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.events.SyncBeginEvent;
import ubicrypt.core.events.SynchDoneEvent;
import ubicrypt.core.exp.AlreadyManagedException;
import ubicrypt.core.provider.FileEvent;
import ubicrypt.core.util.ClassMatcher;
import ubicrypt.core.util.FileInSync;
import ubicrypt.ui.OSUtil;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.functions.Actions.empty;
import static ubicrypt.core.util.SupplierExp.silent;
import static ubicrypt.ui.UItils.emptyPath;
import static ubicrypt.ui.UItils.searchFile;

public class FilesController implements Initializable, ApplicationContextAware {
  private static final Logger log = getLogger(FilesController.class);
  @Inject
  Stage stage;
  @Inject
  LocalConfig localConfig;
  @FXML
  TreeView<ITreeItem> treeView;
  @FXML
  TreeItem<ITreeItem> root;
  @FXML
  Button addFile;
  @Inject
  FileFacade fileCommander;
  @Resource
  Path basePath;
  private final Consumer<Path> folderAdder =
    fromFolder -> {
      DirectoryChooser dc = new DirectoryChooser();
      dc.setInitialDirectory(basePath.resolve(fromFolder).toFile());
      dc.setTitle("Select Folder to Save");
      ofNullable(dc.showDialog(stage))
        .ifPresent(
          folder -> {
            try {
              List<File> files =
                Files.walk(folder.toPath())
                  .filter(Files::isRegularFile)
                  .filter(f -> !Files.isSymbolicLink(f))
                  .filter(f -> silent(() -> !Files.isHidden(f)))
                  .map(Path::toFile)
                  .collect(Collectors.toList());
              filesAdder.accept(files);

            } catch (IOException e) {
              log.error(e.getMessage(), e);
            }
          });
    };
  private final Consumer<Path> fileAdder =
    fromFolder -> {
      FileChooser fc = new FileChooser();
      log.debug("add files from:{}", basePath.resolve(fromFolder));
      fc.setInitialDirectory(basePath.resolve(fromFolder).toFile());
      fc.setTitle("Select Files to Save");
      ofNullable(fc.showOpenMultipleDialog(stage)).ifPresent(filesAdder);
    };
  private final Consumer<List<File>> filesAdder =
    files ->
      Observable.merge(
        files
          .stream()
          .map(
            file -> {
              log.debug("adding file:{}", file);
              final Path relPath = basePath.relativize(file.toPath());
              return fileCommander
                .addFile(file.toPath())
                .flatMap(
                  tupla -> {
                    addFiles(relPath.iterator(), basePath, root, tupla.getT1());
                    return tupla.getT2();
                  })
                .onErrorReturn(
                  err -> {
                    if (err instanceof AlreadyManagedException) {
                      return true;
                    }
                    throw Throwables.propagate(err);
                  })
                .doOnNext(
                  result -> log.info("file:{} add result:{}", file, result));
            })
          .collect(toList()))
        .subscribe(
          empty(),
          err -> {
            log.error(err.getMessage(), err);
          },
          () -> {
          });
  private final Function<UbiFile, Observable<Boolean>> fileUntracker =
    file ->
      fileCommander
        .removeFile(basePath.resolve(file.getPath()))
        .doOnNext(
          res -> {
            log.info("untrack file:{}  result:{}", file, res);
            searchFile(root, file).ifPresent(FilesController::removeItem);
          })
        .doOnError(err -> log.error("error untracking file", err));
  @Resource
  Observable<FileEvent> fileEvents;
  @Inject
  FileInSync fileInSync;
  @Inject
  OSUtil osUtil;
  private String fxml;
  @Inject
  @Qualifier("appEvents")
  private Observable<Object> appEvents;
  private ApplicationContext ctx;

  private static void removeItem(final TreeItem<ITreeItem> item) {
    final TreeItem<ITreeItem> parent = item.getParent();
    parent.getChildren().remove(item);
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    fxml = substringBefore(substringAfterLast(url.getFile(), "/"), ".fxml");
    treeView.setCellFactory(
      treeView1 -> new TreeCellFactory(treeView1, fileUntracker, null, osUtil, basePath));
    localConfig
      .getLocalFiles()
      .stream()
      .filter(Utils.trackedFile)
      .forEach(localFile -> addFiles(localFile.getPath().iterator(), basePath, root, localFile));

    //remote file events
    fileEvents
      .filter(fileEvent -> fileEvent.getLocation() == FileEvent.Location.remote)
      .subscribe(
        fileEvent -> {
          log.debug("file remote event:{}", fileEvent);
          //update file icon
          final UbiFile<UbiFile> file = fileEvent.getFile();
          Observable.create(fileInSync.call(file))
            .subscribe(
              res -> {
                searchFile(root, file)
                  .ifPresent(
                    treeView -> {
                      log.debug("found file item:{}", treeView);
                      final Node graphics = treeView.getValue().getGraphics();
                      graphics.getStyleClass().clear();
                      graphics.getStyleClass().add(format("tree-file-saved-%s", res));
                    });
              });
        });
    //local file events
    fileEvents
      .filter(fileEvent -> fileEvent.getLocation() == FileEvent.Location.local)
      .subscribe(
        fileEvent -> {
          log.debug("file local event:{}", fileEvent);
          Optional<TreeItem<ITreeItem>> optfile = searchFile(root, fileEvent.getFile());
          if (!optfile.isPresent()) {
            optfile =
              localConfig
                .getLocalFiles()
                .stream()
                .filter(fileEvent.getFile()::equals)
                .findFirst()
                .map(
                  fe ->
                    addFiles(
                      fileEvent.getFile().getPath().iterator(), basePath, root, fe));
          }
          if (!optfile.isPresent()) {
            log.debug("file not present:{}", fileEvent.getFile().getPath());
            return;
          }
          TreeItem<ITreeItem> vfile = optfile.get();
          final Node graphics = vfile.getValue().getGraphics();
          graphics.getStyleClass().clear();
          switch (fileEvent.getType()) {
            case created:
            case synched:
              graphics.getStyleClass().add("tree-file-saved-true");
              break;
            case unsynched:
              graphics.getStyleClass().add("tree-file-saving");
              break;
            case error:
              graphics.getStyleClass().add("tree-file-saved-true");
              break;
            case removed:
            case deleted:
              removeItem(vfile);
              break;
            default:
              graphics.getStyleClass().add(format("tree-file-saved-%s", true));
          }
        });

    //sync-done events
    appEvents.subscribe(
      ClassMatcher.newMatcher()
        .on(
          SyncBeginEvent.class,
          event -> {
            log.info("sync begin received");
          })
        .on(
          SynchDoneEvent.class,
          event -> {
            log.debug("synchronization done");
          }));
  }

  private synchronized TreeItem<ITreeItem> addFiles(
    final Iterator<Path> it,
    final Path rootPath,
    final TreeItem<ITreeItem> root,
    final LocalFile file) {
    if (!it.hasNext()) {
      return root;
    }
    final Path path = it.next();
    final Path resolvedPath = rootPath.resolve(path);
    if (Files.isRegularFile(resolvedPath)) {
      final FileItem item = new FileItem(file);
      final TreeItem<ITreeItem> fileItem = new TreeItem<>(item);
      root.getChildren().add(fileItem);
      root.getChildren()
        .sort(
          (iTreeItemTreeItem, t1) ->
            iTreeItemTreeItem.getValue().getLabel().compareTo(t1.getValue().getLabel()));
      return fileItem;
    }
    final Optional<TreeItem<ITreeItem>> optTreeItem =
      root.getChildren()
        .stream()
        .filter(ti -> ((FolderItem) ti.getValue()).getPath().equals(path))
        .findFirst();
    if (optTreeItem.isPresent()) {
      return addFiles(it, resolvedPath, optTreeItem.get(), file);
    }
    final TreeItem<ITreeItem> fileItem =
      new TreeFolderItem(
        new FolderItem(
          path,
          event -> fileAdder.accept(resolvedPath),
          event -> folderAdder.accept(resolvedPath)));
    root.getChildren().add(fileItem);
    root.getChildren()
      .sort(
        (iTreeItemTreeItem, t1) ->
          iTreeItemTreeItem.getValue().getLabel().compareTo(t1.getValue().getLabel()));
    return addFiles(it, resolvedPath, fileItem, file);
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.ctx = applicationContext;
  }

  public void addFile(MouseEvent mouseEvent) {
    fileAdder.accept(emptyPath);
  }

  public void addFolder() {
    folderAdder.accept(basePath.resolve(emptyPath));
  }
}
