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

import com.google.common.base.Throwables;

import org.springframework.context.ConfigurableApplicationContext;

import javafx.util.Callback;

import static ubicrypt.core.Utils.springIt;

public class ControllerFactory implements Callback<Class<?>, Object> {
  private final ConfigurableApplicationContext ctx;

  public ControllerFactory(ConfigurableApplicationContext ctx) {
    this.ctx = ctx;
    ctx.getBeanFactory().registerSingleton("controllerFactory", this);
  }

  @Override
  public Object call(Class<?> aClass) {
    try {
      return springIt(ctx, aClass.newInstance());
    } catch (Exception e) {
      Throwables.propagate(e);
      return null;
    }
  }
}
