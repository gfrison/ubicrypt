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

import org.apache.log4j.WriterAppender;
import org.apache.log4j.spi.LoggingEvent;

import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.Subject;

public class StreamAppender extends WriterAppender {

  private static Subject<String, String> logStream = BufferUntilSubscriber.create();

  public static Subject<String, String> getLogStream() {
    return logStream;
  }

  @Override
  public void append(LoggingEvent event) {
    final String format = getLayout().format(event);
    logStream.onNext(format);
  }
}
