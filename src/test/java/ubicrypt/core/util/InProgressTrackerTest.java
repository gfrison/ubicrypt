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

import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

import rx.Observable;
import rx.subjects.PublishSubject;
import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.UbiFile;

import static org.assertj.core.api.Assertions.assertThat;

public class InProgressTrackerTest {

  final IRepository repository =
      new IRepository() {
        @Override
        public Observable<InputStream> get(UbiFile file) {
          return null;
        }
      };
  private InProgressTracker tracker;
  private PublishSubject<ProgressFile> progressEvents;

  @Before
  public void setUp() throws Exception {
    tracker = new InProgressTracker();
    progressEvents = PublishSubject.create();
    tracker.setProgressEvents(progressEvents);
    tracker.init();
    Thread.sleep(50);
  }

  @Test
  public void track() throws Exception {
    assertThat(tracker.inProgress()).isFalse();
    final LocalFile file1 = new LocalFile();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(
        new ProgressFile(new FileProvenience(file1, repository), repository, true, false));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isFalse();
  }

  @Test
  public void trackMulti() throws Exception {
    assertThat(tracker.inProgress()).isFalse();
    final LocalFile file1 = new LocalFile();
    final LocalFile file2 = new LocalFile();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    progressEvents.onNext(new ProgressFile(new FileProvenience(file2, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(
        new ProgressFile(new FileProvenience(file1, repository), repository, true, false));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(
        new ProgressFile(new FileProvenience(file2, repository), repository, true, false));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isFalse();
  }

  @Test
  public void trackError() throws Exception {
    assertThat(tracker.inProgress()).isFalse();
    final LocalFile file1 = new LocalFile();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(new ProgressFile(new FileProvenience(file1, repository), repository, 10));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isTrue();
    progressEvents.onNext(
        new ProgressFile(new FileProvenience(file1, repository), repository, false, true));
    Thread.sleep(50);
    assertThat(tracker.inProgress()).isFalse();
  }
}
