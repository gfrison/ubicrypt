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
package ubicrypt.ui.bread;

import org.controlsfx.control.BreadCrumbBar;
import org.slf4j.Logger;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.ui.Anchor.anchor;

public class BController implements Initializable, EnvironmentAware {
    private static final Logger log = getLogger(BController.class);
    @FXML
    BreadCrumbBar<BCItem> breadcrumb;
    private Environment env;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        final List<String> stackScene = anchor().getSceneStack();
        final TreeItem<BCItem> treeItem = breadCrumbTree(stackScene.iterator(), null, stackScene.size());
        breadcrumb.selectedCrumbProperty().set(treeItem);
        breadcrumb.setOnCrumbAction(event -> anchor().popScene(event.getSelectedCrumb().getValue().getLevel()));
    }

    private TreeItem<BCItem> breadCrumbTree(final Iterator<String> it, final TreeItem<BCItem> root, int level) {
        if (!it.hasNext()) {
            return root;
        }
        final String next = it.next();
        final TreeItem<BCItem> item = new TreeItem<>(new BCItem(env.getProperty("bc." + next, next), level));
        if (root != null) {
            root.getChildren().add(item);
        }
        return breadCrumbTree(it, item, --level);
    }

    @Override
    public void setEnvironment(final Environment environment) {
        this.env = environment;
    }

}
