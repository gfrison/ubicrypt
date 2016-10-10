/**
 * Copyright (C) 2016 Giancarlo Frison <giancarlo@gfrison.com>
 * <p>
 * Licensed under the UbiCrypt License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://github.com/gfrison/ubicrypt/LICENSE.md
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ubicrypt.core.util;

import org.apache.log4j.Level;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.IntStream.range;
import static org.apache.log4j.LogManager.getRootLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.just;
import static rx.Observable.timer;

public class QueueLinerTest {
    private static final Logger log = getLogger(QueueLinerTest.class);
    private Level prev;

    @Before
    public void setUp() throws Exception {
        prev = getRootLogger().getLevel();
        getRootLogger().setLevel(Level.TRACE);
    }

    @After
    public void tearDown() throws Exception {
        getRootLogger().setLevel(prev);
    }

    @Test
    public void oneEpiloguer() throws Exception {
        final QueueLiner<Integer> liner = new QueueLiner<>(1000);
        final AtomicInteger epilogeCounter1 = new AtomicInteger(0);
        final QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> {
            epilogeCounter1.incrementAndGet();
            return just(0);
        });
        final int[] vals = {0};
        range(0, 11).forEach(i -> epi1.call(timer(10, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io()).subscribe((Action1<Integer>) val -> vals[0] += val));
        sleep(500);
        assertThat(epilogeCounter1.get()).isEqualTo(1);
        assertThat(vals).isEqualTo(new int[]{(int) (5.5 * 10)});
    }

    @Test
    public void twoEpiloguers() throws Exception {
        final QueueLiner<Integer> liner = new QueueLiner<>(1000);
        final AtomicInteger epilogeCounter1 = new AtomicInteger(0);
        final AtomicInteger epilogeCounter2 = new AtomicInteger(0);
        final QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> {
            System.out.println("increment1");
            epilogeCounter1.incrementAndGet();
            return just(0);
        });
        final QueueLiner.QueueEpilogued epi2 = liner.createEpiloguer(() -> {
            System.out.println("increment2");
            epilogeCounter2.incrementAndGet();
            return just(0);
        });
        log.debug("epi1:{}, epi2:{}", epi1.hashCode(), epi2.hashCode());
        final int[] vals = {0};
        range(0, 11).forEach(i -> epi1.call(timer(10, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io()).subscribe((Action1<Integer>) val -> vals[0] += val));
        range(0, 11).forEach(i -> epi2.call(timer(10, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io()).subscribe((Action1<Integer>) val -> vals[0] += val));
        sleep(500);
        assertThat(epilogeCounter1.get()).isEqualTo(1);
        assertThat(epilogeCounter2.get()).isEqualTo(1);
        assertThat(vals).isEqualTo(new int[]{(int) (2 * 5.5 * 10)});
    }

    @Test
    public void checkComplete() throws Exception {
        final QueueLiner<Integer> liner = new QueueLiner<>(1000);
        final AtomicInteger epilogeCounter1 = new AtomicInteger(0);
        final QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> {
            epilogeCounter1.incrementAndGet();
            return just(0);
        });
        final List<Observable<Integer>> ls = range(0, 11).mapToObj((IntFunction<Observable<Integer>>) i -> epi1.call(timer(10, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io())).collect(Collectors.toList());
        final CountDownLatch cd = new CountDownLatch(1);
        Observable.merge(ls).doOnCompleted(cd::countDown).subscribe();
        assertThat(cd.await(1500, MILLISECONDS)).isTrue();

    }

    @Test
    public void check2Complete() throws Exception {
        final QueueLiner<Integer> liner = new QueueLiner<>(1000);
        final AtomicInteger epilogeCounter1 = new AtomicInteger(0);
        final QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> {
            epilogeCounter1.incrementAndGet();
            return just(0);
        });
        IntStream.range(0, 10).forEach(ii -> {
            List<Observable<Integer>> ls = range(0, 11).mapToObj((IntFunction<Observable<Integer>>) i -> epi1.call(timer(10, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io())).collect(Collectors.toList());
            CountDownLatch cd = new CountDownLatch(1);
            Observable.merge(ls).doOnCompleted(cd::countDown).subscribe();
            try {
                assertThat(cd.await(1500, MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        });

    }

    @Test
    public void intermediateEpilogue() throws Exception {
        final QueueLiner<Integer> liner = new QueueLiner<>(200);
        final AtomicInteger epilogeCounter1 = new AtomicInteger(0);
        final QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> {
            epilogeCounter1.incrementAndGet();
            return just(0);
        });
        final int[] vals = {0};
        range(0, 11).forEach(i -> epi1.call(timer(i < 5 ? 10 : 50 + i, MILLISECONDS).map(t -> i)).subscribeOn(Schedulers.io()).subscribe((Action1<Integer>) val -> vals[0] += val));
        sleep(1000);
        assertThat(epilogeCounter1.get()).isEqualTo(2);
        assertThat(vals).isEqualTo(new int[]{(int) (5.5 * 10)});

    }

    @Test
    public void shutdown1() throws Exception {
        AtomicBoolean result = new AtomicBoolean(false);
        AtomicBoolean invokedEpiloguer = new AtomicBoolean(false);
        final QueueLiner<Boolean> liner = new QueueLiner<>(100);
        QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> Observable.create(subscriber -> {
            invokedEpiloguer.set(true);
            subscriber.onNext(true);
            subscriber.onCompleted();
        }));
        epi1.call(timer(100, MILLISECONDS).map(i -> {
            result.set(true);
            return true;
        }).subscribeOn(Schedulers.io())).subscribe();
        long ts = System.currentTimeMillis();
        Thread.sleep(50);
        liner.stop().toBlocking().firstOrDefault(null);
        assertThat(System.currentTimeMillis() - ts).isGreaterThan(90);
        assertThat(result.get()).isTrue();
        assertThat(invokedEpiloguer.get()).isTrue();
    }

    @Test
    public void shutdownMultijobSameEpiloguer() throws Exception {
        AtomicInteger result = new AtomicInteger(0);
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger invokedEpiloguer = new AtomicInteger(0);
        final QueueLiner<Boolean> liner = new QueueLiner<>(100);
        QueueLiner.QueueEpilogued epi1 = liner.createEpiloguer(() -> Observable.create(subscriber -> {
            invokedEpiloguer.incrementAndGet();
            subscriber.onNext(true);
            subscriber.onCompleted();
        }));
        IntStream.range(0, 10).forEach(i -> epi1.call(timer(100, MILLISECONDS).map(y -> {
            result.incrementAndGet();
            return true;
        }).subscribeOn(Schedulers.io()))
                .doOnCompleted(() -> completed.incrementAndGet())
                .subscribe());
        Thread.sleep(50);
        liner.stop().toBlocking().firstOrDefault(null);
        assertThat(result.get()).isEqualTo(1);
        assertThat(invokedEpiloguer.get()).isEqualTo(1);
        assertThat(completed.get()).isEqualTo(10);
    }

    @Test
    public void shutdownMultiEpiloguers() throws Exception {
        AtomicInteger result = new AtomicInteger(0);
        AtomicInteger invokedEpiloguer = new AtomicInteger(0);
        final QueueLiner<Boolean> liner = new QueueLiner<>(100);

        List<QueueLiner<Boolean>.QueueEpilogued> eps = IntStream.range(0, 10).mapToObj(i -> liner.createEpiloguer(() -> Observable.create(subscriber -> {
            invokedEpiloguer.incrementAndGet();
            subscriber.onNext(true);
            subscriber.onCompleted();
        }))).collect(Collectors.toList());
        eps.forEach(ep -> ep.call(timer(100, MILLISECONDS).map(y -> {
            result.incrementAndGet();
            return true;
        }).subscribeOn(Schedulers.io())).subscribe());
        Thread.sleep(50);
        liner.stop().toBlocking().firstOrDefault(null);
        assertThat(result.get()).isEqualTo(1);
        assertThat(invokedEpiloguer.get()).isEqualTo(1);
    }

    @Test
    public void shutdownNoJobs() throws Exception {
        final QueueLiner<Boolean> liner = new QueueLiner<>(100);
        liner.stop().toBlocking().firstOrDefault(null);


    }
}
