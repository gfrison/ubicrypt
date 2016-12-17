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

import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Optional;

import javafx.scene.control.TreeItem;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.ui.files.FileItem;
import ubicrypt.ui.files.FolderItem;
import ubicrypt.ui.files.ITreeItem;

import static org.slf4j.LoggerFactory.getLogger;

public class UItils {
  public static final Path emptyPath = Paths.get("");
  private static final Logger log = getLogger(UItils.class);

  public static Optional<TreeItem<ITreeItem>> searchFile(
    final TreeItem<ITreeItem> filesRoot, final UbiFile file) {
    return searchFile(filesRoot, file, file.getPath().iterator(), emptyPath);
  }

  private static Optional<TreeItem<ITreeItem>> searchFile(
    final TreeItem<ITreeItem> filesRoot,
    final UbiFile file,
    final Iterator<Path> it,
    final Path basePath) {
    if (!it.hasNext()) {
      if (filesRoot.getValue() instanceof FileItem) {
        if (((FileItem) filesRoot.getValue()).getFile().equals(file)) {
          return Optional.of(filesRoot);
        }
      }
      return Optional.empty();
    }
    final Path path = it.next();
    final Path resolvedPath = basePath.resolve(path);
    return filesRoot
      .getChildren()
      .stream()
      .filter(
        item -> {
          if (item.getValue() instanceof FileItem) {
            return ((FolderItem) item.getValue())
              .getPath()
              .toString()
              .equals(resolvedPath.toString());
          }
          return ((FolderItem) item.getValue()).getPath().toString().equals(path.toString());
        })
      .findFirst()
      .flatMap(item -> searchFile(item, file, it, resolvedPath));
  }
}
