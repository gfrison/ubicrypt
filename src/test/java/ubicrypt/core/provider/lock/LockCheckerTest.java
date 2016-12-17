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

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import rx.Observable;
import ubicrypt.core.dto.ProviderLock;
import ubicrypt.core.exp.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.provider.lock.LockStatus.available;
import static ubicrypt.core.provider.lock.LockStatus.expired;
import static ubicrypt.core.provider.lock.LockStatus.unavailable;

public class LockCheckerTest {

  public static final int DURATION_LOCK_MS = 1000;
  AtomicInteger lgc;
  AtomicReference<ProviderLock> lockRef;

  @Before
  public void setUp() throws Exception {
    lgc = new AtomicInteger(0);
    lockRef = new AtomicReference<>();
  }

  @Test
  public void newLockSuccess() throws Exception {
    Observable.OnSubscribe<ProviderLock> newGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockStatus available =
        Observable.create(new LockChecker(1, newGetter, setter, DURATION_LOCK_MS, 50))
            .toBlocking()
            .first();
    assertThat(available).isEqualTo(available);
    check();
  }

  @Test
  public void newLockSuccessExpiresNull() throws Exception {
    Observable.OnSubscribe<ProviderLock> newGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onNext(new ProviderLock());
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockStatus available =
        Observable.create(new LockChecker(1, newGetter, setter, DURATION_LOCK_MS, 50))
            .toBlocking()
            .first();
    assertThat(available).isEqualTo(available);
    check();
  }

  @Test
  public void newLockSuccessWithNotFound() throws Exception {
    Observable.OnSubscribe<ProviderLock> newGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onError(new NotFoundException(null));
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockStatus available =
        Observable.create(new LockChecker(1, newGetter, setter, DURATION_LOCK_MS, 50))
            .toBlocking()
            .first();
    assertThat(available).isEqualTo(available);
    check();
  }

  public void check() {
    assertThat(lgc.get()).isEqualTo(2);
    assertThat(lockRef.get().getDeviceId()).isEqualTo(1);
    assertThat(lockRef.get().getExpires())
        .isGreaterThan(Instant.ofEpochMilli(System.currentTimeMillis() + 1000 - 300));
    assertThat(lockRef.get().getExpires())
        .isLessThan(Instant.ofEpochMilli(System.currentTimeMillis() + 1000 + 200));
  }

  @Test
  public void ownedSuccess() throws Exception {
    Observable.OnSubscribe<ProviderLock> ownedGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onNext(
                  new ProviderLock(1, Instant.ofEpochMilli(System.currentTimeMillis() - 100)));
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockChecker lc = new LockChecker(1, ownedGetter, setter, DURATION_LOCK_MS, 50);
    assertThat(Observable.create(lc).toBlocking().first()).isEqualTo(available);
    check();
  }

  @Test
  public void otherExpired() throws Exception {
    Observable.OnSubscribe<ProviderLock> getter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onNext(
                  new ProviderLock(9, Instant.ofEpochMilli(System.currentTimeMillis() - 100)));
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockChecker lc = new LockChecker(1, getter, setter, DURATION_LOCK_MS, 50);
    assertThat(Observable.create(lc).toBlocking().first()).isEqualTo(available);
    check();
  }

  @Test
  public void otherInUse() throws Exception {
    Observable.OnSubscribe<ProviderLock> getter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onNext(
                  new ProviderLock(9, Instant.ofEpochMilli(System.currentTimeMillis() + 100)));
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockChecker lc = new LockChecker(1, getter, setter, DURATION_LOCK_MS, 50);
    assertThat(Observable.create(lc).toBlocking().first()).isEqualTo(unavailable);
  }

  @Test
  public void otherInUseWait() throws Exception {
    final Instant expires = Instant.ofEpochMilli(System.currentTimeMillis() + 1000);
    Observable.OnSubscribe<ProviderLock> getter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
            case 1:
              subscriber.onNext(new ProviderLock(9, expires));
              subscriber.onCompleted();
              break;
            case 2:
            case 3:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockChecker lc = new LockChecker(1, getter, setter, DURATION_LOCK_MS, 50, 0, 0);
    AtomicInteger ai = new AtomicInteger(0);
    CountDownLatch cd = new CountDownLatch(1);
    Observable.create(lc)
        .subscribe(
            available -> {
              switch (ai.getAndIncrement()) {
                case 0:
                  assertThat(available).isEqualTo(unavailable);
                  break;
                case 1:
                  assertThat(available).isEqualTo(available);
                  cd.countDown();
                  break;
                default:
                  new IllegalStateException();
              }
            });

    assertThat(cd.await(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void ownedBagged() throws Exception {
    Observable.OnSubscribe<ProviderLock> ownedGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onNext(
                  new ProviderLock(1, Instant.ofEpochMilli(System.currentTimeMillis() - 100)));
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(
                  new ProviderLock(9, Instant.ofEpochMilli(System.currentTimeMillis() + 100)));
              subscriber.onCompleted();
              break;
            case 2:
            case 3:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    LockChecker lc = new LockChecker(1, ownedGetter, setter, DURATION_LOCK_MS, 50, 0, 0);
    AtomicInteger ai = new AtomicInteger(0);
    CountDownLatch cd = new CountDownLatch(1);
    Observable.create(lc)
        .subscribe(
            available -> {
              switch (ai.getAndIncrement()) {
                case 0:
                  assertThat(available).isEqualTo(unavailable);
                  break;
                case 1:
                  assertThat(available).isEqualTo(available);
                  cd.countDown();
                  break;
                default:
                  new IllegalStateException();
              }
            });

    assertThat(cd.await(11, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void expires() throws Exception {
    Observable.OnSubscribe<ProviderLock> newGetter =
        subscriber -> {
          switch (lgc.getAndIncrement()) {
            case 0:
              subscriber.onCompleted();
              break;
            case 1:
              subscriber.onNext(lockRef.get());
              subscriber.onCompleted();
              break;
            default:
              throw new IllegalStateException();
          }
        };
    Function<ProviderLock, Observable<Boolean>> setter =
        lock -> {
          lockRef.set(lock);
          return Observable.just(true);
        };
    AtomicInteger ai = new AtomicInteger(0);
    CountDownLatch cd = new CountDownLatch(1);
    AtomicLong delayExpires = new AtomicLong();
    Observable.create(new LockChecker(1, newGetter, setter, DURATION_LOCK_MS, 50))
        .doOnCompleted(() -> cd.countDown())
        .subscribe(
            available -> {
              switch (ai.getAndIncrement()) {
                case 0:
                  assertThat(available).isEqualTo(available);
                  delayExpires.set(System.currentTimeMillis());
                  break;
                case 1:
                  assertThat(available).isEqualTo(expired);
                  final long actual = System.currentTimeMillis() - delayExpires.get();
                  assertThat(actual).isGreaterThanOrEqualTo(DURATION_LOCK_MS - 50);
                  assertThat(actual).isLessThan(DURATION_LOCK_MS);
                  break;
                default:
                  new IllegalStateException();
              }
            });
    assertThat(cd.await(11, TimeUnit.SECONDS)).isTrue();
  }
}
