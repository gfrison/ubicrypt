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

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;

public class ObjectLockSubscriber<T> implements Observable.OnSubscribe<T> {
  private static final ConcurrentHashMap<Object, Tuple2<AtomicBoolean, Queue<Action0>>> locks =
    new ConcurrentHashMap<>();
  private final T obj;
  private final Consumer<T> onCommit;

  public ObjectLockSubscriber(final T obj) {
    this.obj = obj;
    onCommit = null;
  }

  public ObjectLockSubscriber(final T obj, final Consumer<T> onCommit) {
    this.obj = obj;
    this.onCommit = onCommit;
  }

  @Override
  public void call(final Subscriber<? super T> subscriber) {
    final Tuple2<AtomicBoolean, Queue<Action0>> tuple =
      locks.computeIfAbsent(
        obj, (object) -> Tuple.of(new AtomicBoolean(false), new ConcurrentLinkedQueue()));
    final Queue queue = tuple.getT2();
    subscriber.add(
      new Subscription() {
        private final AtomicBoolean unsub = new AtomicBoolean(false);

        @Override
        public void unsubscribe() {
          final Tuple2<AtomicBoolean, Queue<Action0>> tuple = locks.get(obj);
          if (onCommit != null) {
            onCommit.accept(obj);
          }
          tuple.getT1().compareAndSet(true, false);
          final Action0 func = tuple.getT2().poll();
          if (func != null) {
            func.call();
          }
          unsub.set(true);
        }

        @Override
        public boolean isUnsubscribed() {
          return unsub.get();
        }
      });
    final Action0 func =
      () -> {
        subscriber.onNext(obj);
        subscriber.onCompleted();
      };
    if (tuple.getT1().compareAndSet(false, true)) {
      func.call();
    } else {
      queue.offer(func);
    }
  }
}
