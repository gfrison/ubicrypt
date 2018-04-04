package ubicrypt.core.fdx;

import java.util.List;
import java.util.stream.Collectors;

import rx.Observable;
import rx.Subscriber;
import ubicrypt.core.dto.StoredRemoteConfig;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.util.IPersist;

public class RemoteConfigLoader implements Observable.OnSubscribe<RemoteConfig> {
  private final IPersist persist;
  private final StoredRemoteConfig stored;
  private final FDXLoader loader;

  public RemoteConfigLoader(IPersist persist, StoredRemoteConfig stored) {
    this.persist = persist;
    this.stored = stored;
    this.loader = new FDXLoader(persist, stored.getIndex());
  }

  @Override
  public void call(Subscriber<? super RemoteConfig> subscriber) {
    Observable.create(loader)
        .toList()
        .subscribe(list -> subscriber.onNext(new RemoteConfig(stored.getProviders(), list
            .stream()
            .map(fileIndex ->fileIndex.getFiles()
                .stream()
                .map(f -> {
                  IndexRecord ir = new IndexRecord(fileIndex, f);
                  f.getVclock().setObserver(v -> ir.setStatus(IndexRecord.IRStatus.modified));
                  return ir;
                })
                .collect(Collectors.toList()))
            .flatMap(List::stream)
            .collect(Collectors.toSet()), maxFilesPerIndex)), subscriber::onError, subscriber::onCompleted);
  }
}
