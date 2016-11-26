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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import rx.functions.Func1;
import ubicrypt.core.dto.UbiFile;

import static org.slf4j.LoggerFactory.getLogger;

public class FileItem extends FolderItem {
    private static final Logger log = getLogger(FileItem.class);
    private final UbiFile file;

    public FileItem(final UbiFile file) {
        super(file.getPath());
        this.file = file;
        imageView.getStyleClass().clear();
        imageView.getStyleClass().add("tree-file");
    }


    @Override
    public boolean isFile() {
        return true;
    }

/*
    @PostConstruct
    public void isUp2date() {
        subscriber = fileInSync.call(file);
        Observable.create(subscriber)
                .subscribe(inSinch -> {
                    log.debug("file:{} in synch:{}", file.getPath(), inSinch);
                    if (!inSinch) {
                        imageView.getStyleClass().clear();
                        imageView.getStyleClass().add("tree-file-saving");
                    } else {
                        imageView.getStyleClass().clear();
                        imageView.getStyleClass().add("tree-file-saved-true");
                    }
                }, err -> log.error(err.getMessage(), err));
    }
*/

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        final FileItem fileItem = (FileItem) o;

        return new EqualsBuilder()
                .append(file, fileItem.file)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(file)
                .toHashCode();
    }

    public UbiFile getFile() {
        return file;
    }


}
