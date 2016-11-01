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
package ubicrypt.core.provider.s3;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.TransferParams;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.StoreTempFile;

public class S3Provider extends UbiProvider {
    private S3Conf conf;
    @JsonIgnore
    private AmazonS3 client;
    @JsonIgnore
    private String prefix;
    @JsonIgnore
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public Observable<ProviderStatus> init(long userId) {
        return Observable.<ProviderStatus>create(subscriber -> {
            if (conf == null) {
                subscriber.onError(new RuntimeException("conf not specified"));
                return;
            }
            try {
                client = new AmazonS3Client(new AWSCredentials() {
                    @Override
                    public String getAWSAccessKeyId() {
                        return conf.getAccessKeyId();
                    }

                    @Override
                    public String getAWSSecretKey() {
                        return conf.getSecrectKey();
                    }
                });
                if (conf.getRegion() != null) {
                    client.setRegion(Region.getRegion(conf.getRegion()));
                }
                prefix = String.valueOf(userId);
                try {
                    client.createBucket(conf.getBucket());
                } catch (AmazonS3Exception e) {
                    S3Error error = S3Error.from(e.getErrorResponseXml());
                    switch (error.getCode()) {
                        case "BucketAlreadyOwnedByYou":
                            //already present
                            break;
                        default:
                            subscriber.onError(e);
                            return;
                    }
                }
                initialized.set(true);
                subscriber.onNext(ProviderStatus.initialized);
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<String> post(InputStream is) {
        return postLarge(is, new TransferParams());
    }

    @Override
    public Observable<String> postLarge(InputStream is, TransferParams params) {
        String key = prefix + "/" + RandomStringUtils.random(8, "zxcvbnmasdfghjklqwertyuiopZXCVBNMASDFGHJKLQWERTYUIOP1234567890-_!@#$%^&*()+=[]{}|;,.<>");
        return putLarge(key, is, params).map(res -> key);
    }

    @Override
    public Observable<Boolean> delete(String pid) {
        return Observable.<Boolean>create(subscriber -> {
            if (!initialized.get()) {
                subscriber.onError(new RuntimeException("s3 not initialized"));
                return;
            }
            try {
                client.deleteObject(conf.getBucket(), pid);
                subscriber.onNext(true);
                subscriber.onCompleted();
            } catch (AmazonS3Exception e) {
                error(pid, subscriber, e);
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io());
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

    @Override
    public Observable<Boolean> put(String pid, InputStream is) {
        return putLarge(pid, is, new TransferParams());
    }

    @Override
    public Observable<Boolean> putLarge(String pid, InputStream is, TransferParams params) {
        return Observable.<Boolean>create(subscriber -> {
            if (!initialized.get()) {
                subscriber.onError(new RuntimeException("s3 not initialized"));
                return;
            }
            try {
                new StoreTempFile().call(is)
                        .subscribe(path -> {
                            client.putObject(conf.getBucket(), pid, path.toFile());
                            subscriber.onNext(true);
                            subscriber.onCompleted();
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                            }
                        }, subscriber::onError);

            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    @Override
    public Observable<InputStream> get(String pid) {
        return Observable.<InputStream>create(subscriber -> {
            if (!initialized.get()) {
                subscriber.onError(new RuntimeException("s3 not initialized"));
                return;
            }
            try {
                S3Object obj = client.getObject(conf.getBucket(), pid);
                subscriber.onNext(obj.getObjectContent());
                subscriber.onCompleted();
            } catch (AmazonS3Exception e) {
                error(pid, subscriber, e);
            } catch (Exception e) {
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io());
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
}
