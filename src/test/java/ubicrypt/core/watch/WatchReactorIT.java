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

import com.google.common.base.Throwables;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

import javax.inject.Inject;

import reactor.util.function.Tuples;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.BaseConf;
import ubicrypt.core.IFileCommander;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.local.OnNewLocal;
import ubicrypt.core.provider.RemoteCtxConf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.TestUtils.tmp;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(
  value = {BaseConf.class, RemoteCtxConf.class, WatchReactorIT.TestConf.class, WatchConf.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class WatchReactorIT {

  private static final Logger log = getLogger(WatchReactorIT.class);

  @Inject private WatcherBroadcaster watcherBroadcaster;
  @Inject private IFileCommander fc;

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {
    watcherBroadcaster.close();
    Thread.sleep(10);
    TestUtils.deleteDirs();
  }

  @Test
  public void basePath() throws Exception {
    assertThat(watcherBroadcaster.getBasePath()).isEqualTo(tmp);
  }

  @Test
  public void updateFile() throws Exception {
    when(fc.updateFile(any(Path.class))).thenReturn(Observable.just(true));
    when(fc.deleteFile(any(Path.class))).thenReturn(Observable.just(true));
    Files.write(tmp.resolve("file"), "ciao2".getBytes());
    Thread.sleep(50);
    verify(fc).updateFile(eq(tmp.resolve("file")));
  }

  @Test
  public void deleteFile() throws Exception {
    updateFile();
    Thread.sleep(10);
    Files.delete(tmp.resolve("file"));
    Thread.sleep(50);
    verify(fc).deleteFile(eq(tmp.resolve("file")));
  }

  @Test
  public void createFile() throws Exception {
    when(fc.addFile(any(Path.class)))
        .thenReturn(Observable.just(Tuples.of(new LocalFile(), Observable.just(true))));
    when(fc.deleteFile(any(Path.class))).thenReturn(Observable.just(true));
    Files.write(tmp.resolve("folder").resolve("newFile"), "ciao".getBytes());
    Thread.sleep(50);
    verify(fc).addFile(eq(tmp.resolve("folder").resolve("newFile")));
  }

  @Test
  public void createInTrackedSubfolderFile() throws Exception {
    when(fc.addFile(any(Path.class)))
        .thenReturn(Observable.just(Tuples.of(new LocalFile(), Observable.just(true))));
    when(fc.deleteFile(any(Path.class))).thenReturn(Observable.just(true));
    final Path newDir = tmp.resolve("folder").resolve("newDir");
    Files.createDirectories(newDir);
    Thread.sleep(50);
    Files.write(newDir.resolve("newFile"), "ciao".getBytes());
    Thread.sleep(50);
    verify(fc).addFile(eq(newDir.resolve("newFile")));
  }

  @Test
  public void noCreateUntrackedFile() throws Exception {
    when(fc.addFile(any(Path.class)))
        .thenReturn(Observable.just(Tuples.of(new LocalFile(), Observable.just(true))));
    when(fc.deleteFile(any(Path.class))).thenReturn(Observable.just(true));
    Files.write(tmp.resolve("notTracked"), "ciao".getBytes());
    Thread.sleep(50);
    verifyZeroInteractions(fc);
  }

  @Configuration
  static class TestConf {
    @Bean
    public IFileCommander fileCommander() {
      return mock(IFileCommander.class);
    }

    @Bean
    public Subject<Boolean, Boolean> synchProcessing() {
      return PublishSubject.create();
    }

    @Bean
    public Path basePath() {
      return tmp;
    }

    @Bean
    public LocalConfig localConfig() throws IOException {
      TestUtils.createDirs();
      Files.write(tmp.resolve("file"), "ciao".getBytes());
      Files.createDirectories(tmp.resolve("folder"));
      return new LocalConfig() {
        {
          setLocalFiles(
              new HashSet<LocalFile>() {
                {
                  add(
                      new LocalFile() {
                        {
                          setPath(Paths.get("file"));
                        }
                      });
                }
              });
          setTrackedFolders(
              new HashSet<Path>() {
                {
                  add(Paths.get("folder"));
                }
              });
        }
      };
    }

    @Bean
    public int deviceId(Path basePath) {
      return Utils.deviceId();
    }

    @Bean
    public Path rndPath() {
      final String rnd = RandomStringUtils.randomAlphabetic(3);
      final Path path = TestUtils.tmp.resolve(rnd);
      log.debug("created folder:{} \n", path);
      try {
        Files.createDirectories(path);
      } catch (IOException e) {
        Throwables.propagate(e);
      }
      return path;
    }

    @Bean
    public OnNewLocal onNewFileLocal() {
      return new OnNewLocal();
    }

    @Bean
    public LocalRepository localRepository(final Path rndPath) throws IOException {
      return new LocalRepository(rndPath);
    }

    @Bean
    public Subject<Object, Object> appEvents() {
      return PublishSubject.create();
    }
  }
}
