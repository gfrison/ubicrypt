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

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.subjects.Subject;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.events.ShutdownRegistration;

public class LocalConfigPersistor
    implements Observable.OnSubscribe<Void>, EnvironmentAware, IStoppable {
  private final Logger log = LoggerFactory.getLogger(LocalConfigPersistor.class);
  private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);
  @Inject LocalConfig localConfig;
  @Inject IPGPService ipgpService;

  @Autowired
  @Value("${localConf.interval.persistSec:1}")
  private long interval;

  private Environment env;

  @Value("${pgp.enabled:true}")
  private Boolean encrypt = true;

  private Runnable runnable;

  @Resource
  @Qualifier("appEvents")
  private Subject appEvents;

  @PostConstruct
  public void init() {
    if (env != null) {
      encrypt = Boolean.valueOf(env.getProperty("pgp.enabled", "true"));
    }
    log.info("local conf persistor started, pgp enable:{}", encrypt);
    runnable =
        () -> {
          try {
            final byte[] clearBytes = Utils.marshall(localConfig);
            Files.write(
                Utils.configFile(),
                encrypt
                    ? IOUtils.toByteArray(ipgpService.encrypt(new ByteArrayInputStream(clearBytes)))
                    : clearBytes);
          } catch (final Exception e) {
            log.error(e.getMessage(), e);
          }
        };
    executorService.scheduleWithFixedDelay(runnable, interval, 1, TimeUnit.SECONDS);
    appEvents.onNext(new ShutdownRegistration(this));
  }

  @Override
  public Observable<Void> stop() {
    return Observable.<Void>create(
            subscriber -> {
              log.debug("shutdown local config persistor");
              executorService.shutdown();
              try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
                runnable.run();
                subscriber.onCompleted();
              } catch (InterruptedException e) {
                subscriber.onError(e);
              }
            })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public void call(final Subscriber<? super Void> subscriber) {}

  @Override
  public void setEnvironment(final Environment environment) {
    this.env = environment;
  }
}
