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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.Paths;

import ubicrypt.core.TestUtils;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

public class WatcherBroadcasterTest {
  private final Path path = Paths.get("ciao");
  private WatcherBroadcaster watcher;

  @Before
  public void setUp() throws Exception {
    watcher = Mockito.spy(new WatcherBroadcaster(TestUtils.tmp));
    doNothing().when(watcher).dispatch(any(), any());
  }

  @Test
  public void deleteThenCreate() throws Exception {
    watcher.filterDeleteThenCreate(PathEvent.Event.delete, path);
    Thread.sleep(5);
    watcher.filterDeleteThenCreate(PathEvent.Event.create, path);
    Thread.sleep(15);
    verify(watcher).dispatch(PathEvent.Event.update, path);
  }

  @Test
  public void onlyDelete() throws Exception {
    watcher.filterDeleteThenCreate(PathEvent.Event.delete, path);
    Thread.sleep(15);
    verify(watcher).dispatch(PathEvent.Event.delete, path);
  }

  @Test
  public void onlyCreate() throws Exception {
    watcher.filterDeleteThenCreate(PathEvent.Event.create, path);
    Thread.sleep(15);
    verify(watcher).dispatch(PathEvent.Event.create, path);
  }

  @Test
  public void onlyUpdate() throws Exception {
    watcher.filterDeleteThenCreate(PathEvent.Event.create, path);
    Thread.sleep(15);
    verify(watcher).dispatch(PathEvent.Event.create, path);
  }
}
