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

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import javax.inject.Inject;

import rx.Observable;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.UbiProvider;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.zip.Deflater.BEST_COMPRESSION;
import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.Utils.marshallIs;

public class ObjectSerializer implements IObjectSerializer, EnvironmentAware {

    private static final Logger log = getLogger(ObjectSerializer.class);
    private final UbiProvider provider;
    @Inject
    IPGPService pgpService;
    @Value("${pgp.enabled:true}")
    private boolean encrypt = true;

    public ObjectSerializer(final UbiProvider provider) {
        checkNotNull(provider);
        this.provider = provider;
    }


    @Override
    public <T> Observable<T> putObject(final T obj, final RemoteFile descriptor) {
        checkNotNull(descriptor, "file descriptor must not be null");
        checkNotNull(obj, "object must not be null");
        if (descriptor.getName() == null) {
            return provider.post(encrypt(descriptor, marshallIs(obj))).doOnNext(descriptor::setRemoteName)
                    .last()
                    .map(i -> obj)
                    .defaultIfEmpty(obj);
        }
        return provider.put(descriptor.getName(),
                encrypt(descriptor, marshallIs(obj))).last().map(i -> obj)
                .defaultIfEmpty(obj);
    }

    @Override
    public Observable<Boolean> put(final Object obj, final RemoteFile descriptor) {
        checkNotNull(descriptor, "file descriptor must not be null");
        checkNotNull(obj, "object must not be null");
        if (descriptor.getName() == null) {
            return provider.post(encrypt(descriptor, marshallIs(obj)))
                    .doOnNext(descriptor::setRemoteName)
                    .map(str -> true);
        }
        return provider.put(descriptor.getName(),
                encrypt(descriptor, marshallIs(obj)));
    }

    @Override
    public <T> Observable<T> getObject(final RemoteFile descriptor, final Class<T> type) {
        checkNotNull(descriptor, "file descriptor must not be null");
        checkNotNull(type, "type must not be null");
        if (descriptor.getName() == null) {
            return Observable.error(new NotFoundException("pid null"));
        }
        return provider.get(descriptor.getName())
                .map(is -> Utils.unmarshall(decrypt(descriptor, is), type));
    }

    private InputStream encrypt(final RemoteFile file, final InputStream inputStream) {
        if (!encrypt) {
            return inputStream;
        }
        switch (file.getKey().getType()) {
            case aes:
                return AESGCM.encryptIs(file.getKey().getBytes(), new DeflaterInputStream(inputStream, new Deflater(BEST_COMPRESSION)));
            default:
                return pgpService.encrypt(inputStream);
        }
    }

    private InputStream decrypt(final RemoteFile file, final InputStream inputStream) {
        if (!encrypt) {
            return inputStream;
        }
        switch (file.getKey().getType()) {
            case aes:
                return new InflaterInputStream(AESGCM.decryptIs(file.getKey().getBytes(), inputStream));
            default:
                return pgpService.decrypt(inputStream);
        }
    }


    @Override
    public void setEnvironment(final Environment environment) {
        Environment env = environment;
        encrypt = Boolean.valueOf(env.getProperty("pgp.enabled", "true"));
    }

    public void setPgpService(final IPGPService pgpService) {
        this.pgpService = pgpService;
    }
}
