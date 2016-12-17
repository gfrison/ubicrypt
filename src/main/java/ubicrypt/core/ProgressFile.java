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
package ubicrypt.core;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class ProgressFile {
  private final FileProvenience provenience;
  private final IRepository target;
  private final long chunk;
  private final boolean completed;
  private final boolean error;

  public ProgressFile(
      final FileProvenience provenience, final IRepository target, final long chunk) {
    this.provenience = provenience;
    this.target = target;
    this.chunk = chunk;
    completed = false;
    error = false;
  }

  public ProgressFile(final FileProvenience provenience, final long chunk) {
    this(provenience, null, chunk);
  }

  public ProgressFile(
      final FileProvenience provenience,
      final IRepository target,
      final boolean completed,
      final boolean error) {
    this.provenience = provenience;
    this.target = target;
    this.chunk = 0;
    this.completed = completed;
    this.error = error;
  }

  public ProgressFile(
      final FileProvenience provenience, final boolean completed, final boolean error) {
    this(provenience, null, completed, error);
  }

  public FileProvenience getProvenience() {
    return provenience;
  }

  public IRepository getTarget() {
    return target;
  }

  public boolean isCompleted() {
    return completed;
  }

  public boolean isError() {
    return error;
  }

  public long getChunk() {
    return chunk;
  }

  public Direction getDirection() {
    return target == null ? Direction.inbound : Direction.outbound;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    final ProgressFile that = (ProgressFile) o;

    return new EqualsBuilder()
        .append(provenience, that.provenience)
        .append(target, that.target)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(provenience).append(target).toHashCode();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("provenience", provenience)
        .append("target", target)
        .append("chunk", chunk)
        .append("completed", completed)
        .append("error", error)
        .toString();
  }

  public enum Direction {
    inbound,
    outbound,
  }
}
