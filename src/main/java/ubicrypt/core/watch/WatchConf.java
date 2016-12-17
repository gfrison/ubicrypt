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
package ubicrypt.core.watch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;

import rx.subjects.PublishSubject;

@Configuration
public class WatchConf {

  @Bean
  public WatcherBroadcaster watcher(final Path basePath) throws IOException {
    return new WatcherBroadcaster(basePath);
  }

  @Bean
  public WatchReactor watchReactor() {
    return new WatchReactor();
  }

  @Bean
  public PublishSubject<PathEvent> pathStream() {
    return PublishSubject.create();
  }
}
