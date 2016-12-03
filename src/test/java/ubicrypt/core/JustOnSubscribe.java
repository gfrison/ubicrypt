package ubicrypt.core;

import rx.Observable;
import rx.Subscriber;

public class JustOnSubscribe<T> implements Observable.OnSubscribe<T> {
    final T value;

    public JustOnSubscribe(T value) {
        this.value = value;
    }

    @Override
    public void call(Subscriber<? super T> s) {
        s.onNext(value);
        s.onCompleted();
    }
}
