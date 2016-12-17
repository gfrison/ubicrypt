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
package ubicrypt.core.util;

import com.google.common.base.Throwables;

import java.util.function.Function;

import rx.functions.Func1;

public interface FunctionExp<T, R> {
  static <T, R> Function<T, R> silent(final FunctionExp<T, R> wrap) {
    return (T input) -> {
      try {
        return wrap.apply(input);
      } catch (final Exception e) {
        Throwables.propagate(e);
      }
      return null;
    };
  }

  static <T, R> Func1<T, R> silentFunc1(final FunctionExp<T, R> wrap) {
    return (T input) -> {
      try {
        return wrap.apply(input);
      } catch (final Exception e) {
        Throwables.propagate(e);
      }
      return null;
    };
  }

  R apply(T input) throws Exception;
}
