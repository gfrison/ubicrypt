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
package ubicrypt.core.provider.s3;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import ubicrypt.core.Utils;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.StoreTempFile;

import static java.lang.Character.MAX_RADIX;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.substringAfter;

public class S3Provider extends UbiProvider {
  private final transient AtomicBoolean initialized = new AtomicBoolean(false);
  private final transient AtomicLong maxKey = new AtomicLong(0);
  private final transient Function<String, Observable<Tuple2<String, String>>> checker =
      (pid) ->
          Observable.create(
              subscriber -> {
                if (!initialized.get()) {
                  subscriber.onError(new RuntimeException("s3 not initialized"));
                  return;
                }
                String[] ids = pid.split(":");
                if (ids.length != 2) {
                  subscriber.onError(new RuntimeException("Invalid object ID:" + pid));
                  return;
                }
                if (isEmpty(ids[0])) {
                  subscriber.onError(new RuntimeException("no bucket provided in the ID"));
                  return;
                }
                if (isEmpty(ids[1])) {
                  subscriber.onError(new RuntimeException("no key provided in the ID"));
                  return;
                }
                subscriber.onNext(Tuple.of(ids[0], ids[1]));
                subscriber.onCompleted();
              });
  private S3Conf conf;
  private transient AmazonS3 client;
  private transient String prefix;

  @Override
  public Observable<ProviderStatus> init(long userId) {
    return Observable.<ProviderStatus>create(
            subscriber -> {
              if (conf == null) {
                subscriber.onError(new RuntimeException("conf not specified"));
                return;
              }
              try {
                AmazonS3ClientBuilder clientb =
                    AmazonS3ClientBuilder.standard()
                        .withCredentials(
                            new AWSCredentialsProvider() {
                              @Override
                              public AWSCredentials getCredentials() {
                                return new AWSCredentials() {
                                  @Override
                                  public String getAWSAccessKeyId() {
                                    return conf.getAccessKeyId();
                                  }

                                  @Override
                                  public String getAWSSecretKey() {
                                    return conf.getSecrectKey();
                                  }
                                };
                              }

                              @Override
                              public void refresh() {}
                            });
                if (conf.getRegion() != null) {
                  clientb.withRegion(conf.getRegion());
                }
                client = clientb.build();
                prefix = Long.toString(userId, MAX_RADIX) + "/";
                try {
                  client.headBucket(new HeadBucketRequest(conf.getBucket()));
                } catch (AmazonS3Exception e) {
                  switch (e.getErrorCode()) {
                    case "404 Not Found":
                      client.createBucket(conf.getBucket());
                      break;
                    default:
                      subscriber.onError(e);
                      return;
                  }
                }
                maxKey.set(
                    client
                        .listObjects(conf.getBucket(), prefix)
                        .getObjectSummaries()
                        .stream()
                        .map(
                            obj -> {
                              try {
                                return Long.valueOf(
                                    substringAfter(obj.getKey(), prefix), MAX_RADIX);
                              } catch (Exception e) {
                                return 0L;
                              }
                            })
                        .max(Long::compareTo)
                        .orElse(0L));

                initialized.set(true);
                subscriber.onNext(ProviderStatus.initialized);
                subscriber.onCompleted();
              } catch (Exception e) {
                subscriber.onError(e);
              }
            })
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Observable<String> post(InputStream is) {
    String key =
        conf.getBucket() + ":" + prefix + Long.toString(maxKey.incrementAndGet(), MAX_RADIX);
    return put(key, is).map(res -> key);
  }

  @Override
  public Observable<Boolean> delete(String pid) {
    return checker
        .apply(pid)
        .flatMap(
            pids ->
                Observable.<Boolean>create(
                    subscriber -> {
                      if (!initialized.get()) {
                        subscriber.onError(new RuntimeException("s3 not initialized"));
                        return;
                      }
                      try {
                        client.deleteObject(pids.getT1(), pids.getT2());
                        subscriber.onNext(true);
                        subscriber.onCompleted();
                      } catch (AmazonS3Exception e) {
                        error(pid, subscriber, e);
                      } catch (Exception e) {
                        subscriber.onError(e);
                      }
                    }))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Observable<Boolean> put(String pid, InputStream is) {
    return checker
        .apply(pid)
        .flatMap(
            pids ->
                Observable.<Boolean>create(
                    subscriber -> {
                      try {
                        new StoreTempFile()
                            .call(is)
                            .subscribe(
                                path -> {
                                  try {
                                    client.putObject(pids.getT1(), pids.getT2(), path.toFile());
                                    subscriber.onNext(true);
                                    subscriber.onCompleted();
                                  } catch (Exception e) {
                                    subscriber.onError(e);
                                    return;
                                  }
                                  try {
                                    Files.delete(path);
                                  } catch (IOException e) {
                                  }
                                },
                                subscriber::onError);

                      } catch (Exception e) {
                        subscriber.onError(e);
                        Utils.close(is);
                      }
                    }))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public Observable<InputStream> get(String pid) {
    return checker
        .apply(pid)
        .flatMap(
            pids ->
                Observable.<InputStream>create(
                    subscriber -> {
                      if (!initialized.get()) {
                        subscriber.onError(new RuntimeException("s3 not initialized"));
                        return;
                      }
                      InputStream is = null;
                      try {
                        S3Object obj = client.getObject(pids.getT1(), pids.getT2());
                        is = obj.getObjectContent();
                        subscriber.onNext(is);
                        subscriber.onCompleted();
                      } catch (AmazonS3Exception e) {
                        Utils.close(is);
                        error(pid, subscriber, e);
                      } catch (Exception e) {
                        subscriber.onError(e);
                        Utils.close(is);
                      }
                    }))
        .subscribeOn(Schedulers.io());
  }

  @Override
  public void close() {
    initialized.set(false);
  }

  @Override
  public String code() {
    return "s3";
  }

  @Override
  public String providerId() {
    return "s3://" + conf.getBucket();
  }

  public S3Conf getConf() {
    return conf;
  }

  public void setConf(S3Conf conf) {
    this.conf = conf;
  }

  @Override
  public String toString() {
    return providerId();
  }

  private void error(String pid, Subscriber subscriber, AmazonS3Exception e) {
    S3Error error = S3Error.from(e.getErrorResponseXml());
    switch (error.getCode()) {
      case "NoSuchKey":
        subscriber.onError(new NotFoundException(pid));
        break;
      default:
        subscriber.onError(e);
    }
  }

  @JsonIgnore
  public AmazonS3 getClient() {
    return client;
  }
}
