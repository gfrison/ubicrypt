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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import javax.inject.Inject;

import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.events.ShutdownOK;
import ubicrypt.core.events.ShutdownRegistration;
import ubicrypt.core.events.ShutdownRequest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.empty;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
  loader = AnnotationConfigContextLoader.class,
  classes = {ShutterDownIT.Config.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ShutterDownIT {
  private static final Logger log = getLogger(ShutterDownIT.class);
  @Inject private ShutterDown shutterDown;
  @Inject private Subject appEvents;

  @Test
  public void shutodown1() throws Exception {
    final Service service = new Service();
    appEvents.onNext(new ShutdownRegistration(service));
    Thread.sleep(10);
    CountDownLatch cd = new CountDownLatch(1);
    appEvents.filter(event -> event instanceof ShutdownOK).subscribe(next -> cd.countDown());
    appEvents.onNext(new ShutdownRequest());
    assertThat(cd.await(2, SECONDS)).isTrue();
    assertThat(service.getCounter().get()).isEqualTo(1);
  }

  @Test
  public void shutodownN() throws Exception {
    List<Service> services = IntStream.range(0, 10).mapToObj(i -> new Service()).collect(toList());
    services.stream().map(ShutdownRegistration::new).forEach(appEvents::onNext);
    Thread.sleep(100);
    CountDownLatch cd = new CountDownLatch(1);
    appEvents.filter(event -> event instanceof ShutdownOK).subscribe(next -> cd.countDown());
    appEvents.onNext(new ShutdownRequest());
    assertThat(cd.await(2, SECONDS)).isTrue();
    services.stream().forEach(service -> assertThat(service.getCounter().get()).isEqualTo(1));
  }

  static class Service implements IStoppable {
    private AtomicInteger counter = new AtomicInteger(0);

    @Override
    public Observable<Void> stop() {
      log.info("stop service");
      counter.incrementAndGet();
      return empty();
    }

    public AtomicInteger getCounter() {
      return counter;
    }
  }

  @Configuration
  static class Config {
    @Bean
    public ShutterDown shutterDown() {
      return new ShutterDown();
    }

    @Bean
    public Subject appEvents() {
      return PublishSubject.create();
    }
  }
}
