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
package ubicrypt.core.fdx;

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.List;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IPersist;
import ubicrypt.core.util.Reverser;

import static org.slf4j.LoggerFactory.getLogger;
import static rx.Observable.error;
import static rx.Observable.just;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.created;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.modified;
import static ubicrypt.core.fdx.IndexRecord.IRStatus.unchanged;

public class FDXSaver implements Observable.OnSubscribe<List<IndexRecord>> {
  private static final Logger log = getLogger(FDXSaver.class);
  private final IPersist serializer;
  private final List<IndexRecord> records;

  public FDXSaver(IPersist serializer, List<IndexRecord> records) {
    this.serializer = serializer;
    this.records = records;
  }

  @Override
  public void call(Subscriber<? super List<IndexRecord>> subscriber) {
    //reverse list
    List<IndexRecord> reverse = records.stream().collect(new Reverser<>());

    //save records
    saveCascade(reverse.iterator(), unchanged, just(new RemoteFile()))
        .last()
        .map(
            rff -> {
              List<IndexRecord> front = reverse.stream().collect(new Reverser<>());
              if (!front.isEmpty()) {
                //set initial remote file
                final RemoteFile remoteFile = front.get(0).getRemoteFile();
                remoteFile.copyFrom(rff);
              }
              return front;
            })
        .defaultIfEmpty(reverse.stream().collect(new Reverser<>()))
        .subscribe((Observer<? super Object>) subscriber);
  }

  private Observable<RemoteFile> saveCascade(
      Iterator<IndexRecord> it, IndexRecord.IRStatus previousStatus, Observable<RemoteFile> chain) {
    if (!it.hasNext()) {
      return chain;
    }
    IndexRecord ir = it.next();
    if (previousStatus == created) {
      if (ir.getStatus() != created) {
        ir.setStatus(modified);
      }
      return saveCascade(
          it,
          ir.getStatus(),
          chain.flatMap(
              rf -> {
                if (ir.getFileIndex().getNextIndex() == null) {
                  ir.getFileIndex().setNextIndex(rf);
                } else {
                  ir.getFileIndex().getNextIndex().copyFrom(rf);
                }
                return serializer
                    .put(ir.getFileIndex(), ir.getRemoteFile())
                    .map(r -> ir.getRemoteFile());
              }));
    }
    return saveCascade(it, ir.getStatus(), chain.flatMap(rf -> action(ir, rf)));
  }

  private Observable<RemoteFile> action(IndexRecord ir, RemoteFile previous) {
    switch (ir.getStatus()) {
      case unchanged:
        return just(ir.getRemoteFile());
      case created:
      case modified:
        return serializer
            .put(ir.getFileIndex(), ir.getRemoteFile())
            .map(r -> ir.getRemoteFile())
            .defaultIfEmpty(ir.getRemoteFile());
      case deleted:
        return serializer.delete(ir.getRemoteFile()).map(r -> previous).defaultIfEmpty(previous);
      default:
        return error(new IllegalStateException(ir.getStatus() + " not managed"));
    }
  }
}
