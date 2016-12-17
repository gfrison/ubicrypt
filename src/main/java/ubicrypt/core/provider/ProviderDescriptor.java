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
package ubicrypt.core.provider;

import javafx.scene.image.ImageView;

public class ProviderDescriptor {
  private final Class type;
  private final String code;
  private final String description;
  private final ImageView logo;

  public ProviderDescriptor(
    final Class type, final String code, final String description, ImageView logo) {
    this.type = type;
    this.code = code;
    this.description = description;
    this.logo = logo;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return code + " - " + description;
  }

  public Class getType() {
    return type;
  }

  public ImageView getLogo() {
    return logo;
  }
}
