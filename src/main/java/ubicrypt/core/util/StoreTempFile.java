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

import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import rx.Observable;
import rx.functions.Func1;
import ubicrypt.core.Utils;

import static org.slf4j.LoggerFactory.getLogger;

public class StoreTempFile implements Func1<InputStream, Observable<Path>> {
  private static final Logger log = getLogger(StoreTempFile.class);

  @Override
  public Observable<Path> call(final InputStream inputStream) {
    try {
      final Path tempFile = Files.createTempFile(null, null);
      log.debug("stored on temp file:{}", tempFile);
      return Utils.write(tempFile, inputStream).lastOrDefault(0L).map((n -> tempFile));
    } catch (final Exception e) {
      Utils.close(inputStream);
      return Observable.error(e);
    }
  }
}
