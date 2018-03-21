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
package ubicrypt.core.dto;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import rx.Observable;
import rx.schedulers.Schedulers;
import ubicrypt.core.util.ObjectLockSubscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectLockSubscriberTest {
  @Test
  public void testName() throws Exception {
    final CountDownLatch cd = new CountDownLatch(1);
    Observable.create(new ObjectLockSubscriber<>("ciao")) //
        .subscribe(
            str -> {
              assertThat(str).isEqualTo("ciao");
              cd.countDown();
            });
    if (!cd.await(1, TimeUnit.SECONDS)) {
      Assertions.fail("fail");
    }
  }

  @Test
  public void test2() throws Exception {
    final CountDownLatch cd = new CountDownLatch(2);
    Observable.create(new ObjectLockSubscriber<>("ciao")) //
        .subscribeOn(Schedulers.io()) //
        .subscribe(
            str -> {
              assertThat(str).isEqualTo("ciao");
              cd.countDown();
            });
    Observable.create(new ObjectLockSubscriber<>("ciao")) //
        .subscribeOn(Schedulers.io()) //
        .subscribe(
            str -> {
              assertThat(str).isEqualTo("ciao");
              cd.countDown();
            });
    if (!cd.await(1, TimeUnit.SECONDS)) {
      Assertions.fail("fail");
    }
  }

  @Test
  @Ignore
  public void test3() throws Exception {
    final String[] strs = new String[1];
    final CountDownLatch cd = new CountDownLatch(1);
    Observable.create(new ObjectLockSubscriber<>("ciao", str -> strs[0] = str)) //
        .flatMap(str -> Observable.just(1)) //
        .flatMap(i -> Observable.just(2)) //
        .subscribe(
            i -> {
              assertThat(i).isEqualTo(2);
              cd.countDown();
            });
    if (!cd.await(1, TimeUnit.SECONDS)) {
      Assertions.fail("fail");
    }
    assertThat(strs[0]).isEqualTo("ciao");
  }

  @Test
  public void test4() throws Exception {
    final Test1 obj = new Test1();
    final int count = 10;
    final CountDownLatch cd = new CountDownLatch(count);
    IntStream.range(0, count)
        .boxed()
        .map(
            i ->
                Observable.create(
                        new ObjectLockSubscriber<>(
                            obj,
                            param -> {
                              try {
                                Thread.sleep(new Random().nextInt((100 - 50) + 1) + 50);
                              } catch (InterruptedException e) {
                                e.printStackTrace();
                              }
                              param.val++;
                            })) //
                    .subscribeOn(Schedulers.io())
                    .subscribe(n -> cd.countDown()))
        .collect(Collectors.toList());
    if (!cd.await(9, TimeUnit.SECONDS)) {
      Assertions.fail("fail");
    }
    Thread.sleep(100);
    assertThat(obj.val).isEqualTo(count);
  }

  @Test
  public void testCompose() throws Exception {
    final AtomicReference<String> ciao = new AtomicReference<>("ciao");
    Observable.create(
            new ObjectLockSubscriber<AtomicReference>(ciao, str -> str.set(str.get() + "!"))) //
        .flatMap(
            (AtomicReference ref) -> {
              assertThat(ref.get()).isEqualTo("ciao");
              return Observable.just(1);
            })
        .toBlocking()
        .first();
    Thread.sleep(10);
    assertThat(ciao.get()).isEqualTo("ciao!");
  }

  static class Test1 {
    public int val = 0;
  }
}
