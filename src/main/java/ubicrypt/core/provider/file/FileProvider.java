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
package ubicrypt.core.provider.file;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

import rx.Observable;
import ubicrypt.core.Utils;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static com.google.common.base.Preconditions.checkNotNull;

public class FileProvider extends UbiProvider {
    private final static transient Logger log = LoggerFactory.getLogger(FileProvider.class);

    private FileConf conf;

    @Override
    public Observable<String> post(final InputStream is) {
        checkNotNull(is, "input stream must be not null");
        final String id = UUID.randomUUID().toString();
        return Observable.concat(Utils.write(conf.getPath().resolve(id), is).map(i -> (String) null), Observable.just(id))
                .doOnSubscribe(() -> log.debug("post {}", conf.getPath().resolve(id))).last();
    }

    @Override
    public Observable<Boolean> delete(final String pid) {
        checkNotNull(pid, "pid must be not null");
        return Observable.create(subscriber -> {
            try {
                log.debug("delete {}", conf.getPath().resolve(pid));
                Files.delete(conf.getPath().resolve(pid));
                subscriber.onNext(true);
                subscriber.onCompleted();
            } catch (final IOException e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Observable<Boolean> put(final String pid, final InputStream is) {
        checkNotNull(pid, "pid must be not null");
        checkNotNull(is, "input stream must be not null");
        return Utils.write(conf.getPath().resolve(pid), is)
                .doOnSubscribe(() -> log.debug("put {}", conf.getPath().resolve(pid))).last().map(l -> true).defaultIfEmpty(false);
    }


    @Override
    public Observable<InputStream> get(final String pid) {
        checkNotNull(pid, "pid must be not null");
        return Observable.create(subscriber -> {
            try {
                log.debug("get {}", conf.getPath().resolve(pid));
                subscriber.onNext(Utils.readIs(conf.getPath().resolve(pid)));
                subscriber.onCompleted();
            } catch (final Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public String providerId() {
        return "file:/" + conf.getPath().toString();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE).append("dir", conf.getPath()).build();
    }

    @Override
    public Observable<ProviderStatus> init(final long userId) {
        if (!Files.exists(getConf().getPath())) {
            try {
                log.warn("folder:{} does not exist", getConf().getPath());
                Files.createDirectories(getConf().getPath());
            } catch (IOException e) {
                log.error("unable to create directories", e);
                return Observable.just(ProviderStatus.error);
            }
        }
        return super.init(userId);
    }

    public FileConf getConf() {
        return conf;
    }

    public void setConf(final FileConf conf) {
        this.conf = conf;
    }
}
