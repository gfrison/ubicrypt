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

import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public class ResourceBundleWrapper extends ResourceBundle {

  private final ResourceBundle bundle;

  public ResourceBundleWrapper(final ResourceBundle bundle) {
    this.bundle = bundle;
  }

  @Override
  protected Object handleGetObject(final String key) {
    return bundle.getObject(key);
  }

  @Override
  public Enumeration<String> getKeys() {
    return bundle.getKeys();
  }

  @Override
  public boolean containsKey(final String key) {
    return bundle.containsKey(key);
  }

  @Override
  public Locale getLocale() {
    return bundle.getLocale();
  }

  @Override
  public Set<String> keySet() {
    return bundle.keySet();
  }
}
