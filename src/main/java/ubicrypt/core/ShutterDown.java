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

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import rx.Observable;
import rx.subjects.Subject;
import ubicrypt.core.events.ShutdownOK;
import ubicrypt.core.events.ShutdownRegistration;
import ubicrypt.core.events.ShutdownRequest;
import ubicrypt.core.util.ClassMatcher;

import static org.slf4j.LoggerFactory.getLogger;
import static rx.functions.Actions.empty;
import static ubicrypt.core.Utils.logError;

public class ShutterDown {
  private static final Logger log = getLogger(ShutterDown.class);
  private CopyOnWriteArrayList<IStoppable> shutdownRegistered = new CopyOnWriteArrayList<>();

  @Resource
  @Qualifier("appEvents")
  private Subject appEvents;

  @PostConstruct
  public void init() {
    appEvents.subscribe(
        ClassMatcher.newMatcher()
            .on(
                ShutdownRegistration.class,
                registration -> {
                  log.debug("shutdown registered:{}", registration.getRef());
                  shutdownRegistered.add(registration.getRef());
                })
            .on(
                ShutdownRequest.class,
                shutdown -> {
                  Observable.merge(
                          shutdownRegistered
                              .stream()
                              .map(IStoppable::stop)
                              .collect(Collectors.toList()))
                      .subscribe(
                          empty(),
                          logError,
                          () -> {
                            log.debug("all services shutdown");
                            appEvents.onNext(new ShutdownOK());
                          });
                }));
  }
}
