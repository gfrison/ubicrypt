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
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import ubicrypt.core.ProgressFile;
import ubicrypt.core.dto.UbiFile;

import static org.slf4j.LoggerFactory.getLogger;

/** Keep track of ongoing upload/download */
public class InProgressTracker {
  private static final Logger log = getLogger(InProgressTracker.class);
  /** keep the state of uploads/downloads */
  private final AtomicInteger inProgressCounter = new AtomicInteger();

  private final ConcurrentHashMap<UbiFile, Boolean> inProgressList = new ConcurrentHashMap<>();

  @Resource
  @Qualifier("progressEvents")
  private Observable<ProgressFile> progressEvents;

  @PostConstruct
  public void init() {
    progressEvents.subscribe(
        event -> {
          if (event.isCompleted() || event.isError()) {
            if (inProgressList.remove(event.getProvenience().getFile()) != null) {
              if (inProgressCounter.decrementAndGet() < 0) {
                log.warn("inProgressCounter < 0");
              }
            }
          } else {
            inProgressList.computeIfAbsent(
                event.getProvenience().getFile(),
                file -> {
                  inProgressCounter.incrementAndGet();
                  return true;
                });
          }
        });
  }

  public boolean inProgress() {
    return inProgressCounter.get() > 0;
  }

  public void setProgressEvents(Observable<ProgressFile> progressEvents) {
    this.progressEvents = progressEvents;
  }
}
