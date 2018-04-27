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
package ubicrypt.core.exp;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import ubicrypt.core.dto.UbiFile;

public class ConflictException extends RuntimeException {

  private final Tuple2<UbiFile, UbiFile> files;

  public <T extends UbiFile> ConflictException(final UbiFile tUbiFile, final UbiFile file) {
    this.files = Tuples.of(tUbiFile, file);
  }

  public Tuple2<UbiFile, UbiFile> getFiles() {
    return files;
  }
}
