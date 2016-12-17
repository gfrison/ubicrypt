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

import com.google.common.collect.ImmutableList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import javafx.scene.image.ImageView;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.gdrive.GDriveProvider;
import ubicrypt.core.provider.s3.S3Provider;

@Configuration
public class UIConf {
  /** providers */
  @Bean
  public List<ProviderDescriptor> providerDescriptors() {
    return ImmutableList.of(
        new ProviderDescriptor(
            FileProvider.class, "file", "local folder", new ImageView("images/folder-48.png")),
        new ProviderDescriptor(
            S3Provider.class, "s3", "Amazon S3", new ImageView("images/Amazon-48.png")),
        new ProviderDescriptor(
            GDriveProvider.class, "gdrive", "Google Drive", new ImageView("images/gdrive.png")));
  }
}
