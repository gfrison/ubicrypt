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
package ubicrypt.core.provider.lock;

import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Actions;
import ubicrypt.core.dto.ProviderLock;
import ubicrypt.core.exp.NotFoundException;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.empty;
import static rx.Observable.error;
import static rx.Observable.timer;
import static ubicrypt.core.provider.lock.LockStatus.available;
import static ubicrypt.core.provider.lock.LockStatus.expired;
import static ubicrypt.core.provider.lock.LockStatus.unavailable;

public class LockChecker implements Observable.OnSubscribe<LockStatus> {
  private static final Logger log = getLogger(LockChecker.class);
  private final int deviceId;
  private final Observable.OnSubscribe<ProviderLock> lockGetter;
  private final Function<ProviderLock, Observable<Boolean>> lockSetter;
  private final long durationLockMs;
  private final long delayAcquiringLockMs;
  private final long minDelayAttempt;
  private final long maxDelayAttempt;
  /**
   * are currently downloads/uploads in progress? By default, not.
   */
  private Supplier<Boolean> shouldExtendLock = () -> false;

  public LockChecker(
    int deviceId,
    Observable.OnSubscribe<ProviderLock> lockGetter,
    Function<ProviderLock, Observable<Boolean>> lockSetter,
    long durationLockMs,
    long delayAcquiringLockMs) {
    this.deviceId = deviceId;
    this.lockGetter = lockGetter;
    this.lockSetter = lockSetter;
    this.durationLockMs = durationLockMs;
    this.delayAcquiringLockMs = delayAcquiringLockMs;
    minDelayAttempt = 1000;
    maxDelayAttempt = 10000;
  }

  public LockChecker(
    int deviceId,
    Observable.OnSubscribe<ProviderLock> lockGetter,
    Function<ProviderLock, Observable<Boolean>> lockSetter,
    long durationLockMs,
    long delayAcquiringLockMs,
    int minDelayAttempt,
    int maxDelayAttempt) {
    this.deviceId = deviceId;
    this.lockGetter = lockGetter;
    this.lockSetter = lockSetter;
    this.durationLockMs = durationLockMs;
    this.delayAcquiringLockMs = delayAcquiringLockMs;
    this.minDelayAttempt = minDelayAttempt;
    this.maxDelayAttempt = maxDelayAttempt;
  }

  @Override
  public void call(Subscriber<? super LockStatus> subscriber) {
    AtomicReference<ProviderLock> lockRef = new AtomicReference<>();
    Observable.create(lockGetter)
      .onErrorResumeNext(
        err -> {
          if (err instanceof NotFoundException) {
            return empty();
          }
          return error(err);
        })
      .subscribe(
        lockRef::set,
        subscriber::onError,
        () -> {
          try {
            ProviderLock pl = lockRef.get();
            if (pl == null) {
              log.info("lock not present, creating new one");
              pl = new ProviderLock(deviceId, nextExpires());
              grantExclusive(subscriber, pl);
              return;
            }
            if (pl.getDeviceId() == deviceId) {
              log.info("lock owned");
              pl.setExpires(nextExpires());
              grantExclusive(subscriber, pl);
              return;
            }
            if (pl.getExpires() == null || pl.getExpires().isBefore(Instant.now())) {
              log.info("lock expired, attempting to acquire it");
              pl.setDeviceId(deviceId);
              pl.setExpires(nextExpires());
              grantExclusive(subscriber, pl);
              return;
            }
            log.info("lock not available. expires on:{}", pl.getExpires());
            nextAttempt(subscriber, pl);
            subscriber.onNext(unavailable);
          } catch (Exception e) {
            subscriber.onError(e);
          }
        });
  }

  private void grantExclusive(Subscriber<? super LockStatus> subscriber, ProviderLock pl) {
    lockSetter
      .apply(pl)
      .subscribe(
        result -> {
          if (result) {
            timer(delayAcquiringLockMs, MILLISECONDS)
              .flatMap(t -> Observable.create(lockGetter))
              .subscribe(
                lock -> {
                  if (lock.getDeviceId() == deviceId) {
                    log.info("lock acquired");
                    subscriber.onNext(available);
                    expire(subscriber);
                    return;
                  }
                  log.info("lock bagged, expires on:{}", lock.getExpires());
                  subscriber.onNext(unavailable);
                  nextAttempt(subscriber, lock);
                },
                subscriber::onError);
            return;
          }
          subscriber.onNext(unavailable);
          timer(3, SECONDS).subscribe(t -> call(subscriber));
        });
  }

  private void expire(Subscriber<? super LockStatus> subscriber) {
    timer(durationLockMs - delayAcquiringLockMs, MILLISECONDS)
      .subscribe(
        i -> {
          log.info("lock should be extended:{}", shouldExtendLock.get());
          if (shouldExtendLock.get()) {
            lockSetter
              .apply(new ProviderLock(deviceId, nextExpires()))
              .subscribe(
                Actions.empty(),
                err -> log.error("error on renewing lock", err),
                () -> expire(subscriber));
          } else {
            subscriber.onNext(expired);
            //terminate the cycle
            subscriber.onCompleted();
          }
        });
  }

  private void nextAttempt(Subscriber<? super LockStatus> subscriber, ProviderLock lock) {
    final long delay =
      lock.getExpires().toEpochMilli()
        - System.currentTimeMillis()
        + RandomUtils.nextLong(minDelayAttempt, maxDelayAttempt);
    log.debug(
      "lock attempt acquire on:{}", Instant.ofEpochMilli(delay + System.currentTimeMillis()));
    timer(delay, MILLISECONDS)
      .subscribe(Actions.empty(), subscriber::onError, () -> call(subscriber));
  }

  private Instant nextExpires() {
    return Instant.ofEpochMilli(System.currentTimeMillis() + durationLockMs);
  }

  public void setShouldExtendLock(Supplier<Boolean> shouldExtendLock) {
    requireNonNull(shouldExtendLock, "shouldExtendLock must be not null");
    this.shouldExtendLock = shouldExtendLock;
  }
}
