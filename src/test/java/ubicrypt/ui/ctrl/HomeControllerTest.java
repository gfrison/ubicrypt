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
package ubicrypt.ui.ctrl;

import de.saxsys.javafx.test.JfxRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Paths;
import java.util.Optional;

import javafx.scene.control.TreeItem;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.ui.UItils;
import ubicrypt.ui.files.FileItem;
import ubicrypt.ui.files.FolderItem;
import ubicrypt.ui.files.ITreeItem;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JfxRunner.class)
public class HomeControllerTest {

  @Test
  public void searchFile() {
    final TreeItem<ITreeItem> filesRoot = new TreeItem<>();
    final TreeItem<ITreeItem> dirA = new TreeItem<>(new FolderItem(Paths.get("dirA"), null, null));
    filesRoot.getChildren().add(dirA);
    final LocalFile fileA =
        new LocalFile() {
          {
            setPath(Paths.get("dirA/fileA"));
          }
        };
    dirA.getChildren().add(new TreeItem<>(new FileItem(fileA)));

    final Optional<TreeItem<ITreeItem>> opt = UItils.searchFile(filesRoot, fileA);
    assertThat(opt).isPresent();
    assertThat(((FileItem) opt.get().getValue()).getFile()).isEqualTo(fileA);
  }
}
