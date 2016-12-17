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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.function.Function;

public class EqualsValue<T> {
  private final T value;
  private final Function<T, Object> equalsValue;

  public EqualsValue(final T value, final Function<T, Object> equalsValue) {
    this.value = value;
    this.equalsValue = equalsValue;
  }

  public T getValue() {
    return value;
  }

  private Object equalsValue() {
    return equalsValue.apply(value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    final EqualsValue<?> that = (EqualsValue<?>) o;

    return new EqualsBuilder().append(equalsValue(), that.equalsValue()).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(equalsValue()).toHashCode();
  }
}
