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

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;

import static org.assertj.core.api.Assertions.assertThat;

public class OnSubscribeInputStreamTest {

  @Test
  public void testBig() throws Exception {
    final AtomicLong count = new AtomicLong();
    final CountDownLatch cd = new CountDownLatch(1);
    Observable.create(
            new OnSubscribeInputStream(
                new ByteArrayInputStream(StringUtils.repeat('a', 2 << 16).getBytes()), 1 << 16))
        .doOnCompleted(cd::countDown)
        .subscribe(next -> count.addAndGet(next.length));
    assertThat(cd.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(count.get()).isEqualTo(2 << 16);
  }

  @Test
  public void testBig2() throws Exception {
    final AtomicLong count = new AtomicLong();
    final CountDownLatch cd = new CountDownLatch(1);
    Observable.create(
            new OnSubscribeInputStream(
                new ByteArrayInputStream(StringUtils.repeat('a', (2 << 16) - 1).getBytes()),
                1 << 16))
        .doOnCompleted(cd::countDown)
        .subscribe(next -> count.addAndGet(next.length));
    assertThat(cd.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(count.get()).isEqualTo((2 << 16) - 1);
  }
}
