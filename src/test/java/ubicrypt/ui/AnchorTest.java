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

import com.sun.javafx.application.PlatformImpl;

import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Optional;

import javafx.scene.control.TreeItem;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.ui.files.FileItem;
import ubicrypt.ui.files.ITreeItem;

import static org.assertj.core.api.Assertions.assertThat;

public class AnchorTest {
  @Before
  public void setUp() throws Exception {
    PlatformImpl.startup(() -> {});
  }

  @Test
  public void search1() throws Exception {
    final LocalFile file =
        new LocalFile() {
          {
            setPath(Paths.get("prova"));
          }
        };
    final TreeItem<ITreeItem> treeItem = new TreeItem<>();
    treeItem.getChildren().add(new TreeItem<>(new FileItem(file)));
    final Optional<TreeItem<ITreeItem>> opt = UItils.searchFile(treeItem, file);
    assertThat(opt).isPresent();
  }

  @Test
  public void search2() throws Exception {
    final LocalFile file =
        new LocalFile() {
          {
            setPath(Paths.get("prova"));
          }
        };
    final TreeItem<ITreeItem> treeItem = new TreeItem<>();
    treeItem
        .getChildren()
        .add(
            new TreeItem<>(
                new FileItem(
                    new LocalFile() {
                      {
                        setPath(Paths.get("nonprova"));
                      }
                    })));
    treeItem.getChildren().add(new TreeItem<>(new FileItem(file)));
    final Optional<TreeItem<ITreeItem>> opt = UItils.searchFile(treeItem, file);
    assertThat(opt).isPresent();
  }
}
