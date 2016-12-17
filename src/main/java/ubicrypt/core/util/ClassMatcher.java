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

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import rx.functions.Action1;

public class ClassMatcher<T> implements Function<Object, T>, Action1<T> {

  private final BiFunction<Object, Function<Object, T>, T> binder;

  private ClassMatcher(BiFunction<Object, Function<Object, T>, T> next) {
    this.binder = next;
  }

  public static <T> ClassMatcher<T> newMatcher() {
    return new ClassMatcher<>((a, b) -> null);
  }

  @Override
  public T apply(Object obj) {
    return Optional.ofNullable(binder.apply(obj, null)) //
      .orElse(null);
  }

  @Override
  public void call(Object obj) {
    apply(obj);
  }

  public <Y> ClassMatcher<T> on(final Class<Y> targetClass, final Action1<Y> func) {
    return new ClassMatcher<T>(
      (obj, next) ->
        Optional.ofNullable(binder.apply(obj, next)) //
          .orElseGet(
            () -> {
              if (targetClass.isAssignableFrom(obj.getClass())) {
                func.call((Y) obj);
              }
              return null;
            }));
  }

  public <Y> ClassMatcher<T> on(final Class<Y> targetClass, final Function<Y, T> func) {
    return new ClassMatcher<>(
      (obj, next) ->
        Optional.ofNullable(binder.apply(obj, next)) //
          .orElseGet(
            () -> {
              if (targetClass.isAssignableFrom(obj.getClass())) {
                return func.apply((Y) obj);
              }
              return null;
            }));
  }

  public ClassMatcher<T> onDefault(final Function<Object, T> func) {
    return new ClassMatcher<>(
      (obj, next) ->
        Optional.ofNullable(binder.apply(obj, next)).orElseGet(() -> func.apply(obj)));
  }
}
