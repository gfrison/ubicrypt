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

import com.google.common.collect.ImmutableSet;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class Reverser<T> implements Collector<T, List<T>, List<T>> {
  @Override
  public Supplier<List<T>> supplier() {
    return LinkedList::new;
  }

  @Override
  public BiConsumer<List<T>, T> accumulator() {
    return (list, item) -> list.add(0, item);
  }

  @Override
  public BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      l1.addAll(0, l2);
      return l1;
    };
  }

  @Override
  public Function<List<T>, List<T>> finisher() {
    return (list) -> list;
  }

  @Override
  public Set<Characteristics> characteristics() {
    return ImmutableSet.of();
  }
}
