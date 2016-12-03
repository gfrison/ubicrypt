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
package ubicrypt.core.provider.ftp;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.security.ProviderException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import rx.Observable;
import rx.schedulers.Schedulers;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;
import ubicrypt.core.provider.UbiProvider;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.slf4j.LoggerFactory.getLogger;

public class FTProvider extends UbiProvider {
    private static final transient Logger log = getLogger(FTProvider.class);


    protected FTPConf conf;

    private static String showServerReply(FTPClient ftpClient) {
        final String[] replyStrings = ftpClient.getReplyStrings();
        if (replyStrings != null) {
            return Arrays.stream(replyStrings).collect(Collectors.joining());
        }
        return "";
    }

    @Override
    public Observable<ProviderStatus> init(final long userId) {
        return connect().map(bool -> ProviderStatus.initialized);
    }

    @Override
    public String code() {
        return "ftp";
    }

    private Observable<FTPClient> connect() {
        return Observable.<FTPClient>create(subscriber -> {
            final FTPClient client = new FTPClient();
            try {
                client.connect(conf.getHost(), getConf().getPort() == -1 ? 21 : getConf().getPort());
                final int reply = client.getReplyCode();
                if (!FTPReply.isPositiveCompletion(reply)) {
                    log.error("FTP server refused connection:" + client.getReplyString());
                    if (client.isConnected()) {
                        client.disconnect();
                    }
                    subscriber.onError(new RuntimeException("FTP server refused connection:" + client.getReplyString()));
                    return;
                }
                if (!getConf().isAnonymous()) {
                    if (!client.login(getConf().getUsername(), new String(getConf().getPassword()))) {
                        client.disconnect();
                        log.warn("FTP wrong credentials:" + client.getReplyString());
                        subscriber.onError(new RuntimeException("FTP wrong credentials"));
                    }
                }
                client.setFileType(FTP.BINARY_FILE_TYPE);
                client.setBufferSize(1 << 64);
                client.enterLocalPassiveMode();
                client.setControlKeepAliveTimeout(60 * 60); //1h
                if (!isEmpty(conf.getFolder())) {
                    final String directory = startsWith("/", conf.getFolder()) ? conf.getFolder() : "/" + conf.getFolder();
                    if (!client.changeWorkingDirectory(directory)) {
                        if (!client.makeDirectory(directory)) {
                            disconnect(client);
                            subscriber.onError(new ProviderException(showServerReply(client)));
                            return;
                        }
                        if (!client.changeWorkingDirectory(directory)) {
                            disconnect(client);
                            subscriber.onError(new ProviderException(showServerReply(client)));
                            return;
                        }
                    }
                }
                subscriber.onNext(client);
                subscriber.onCompleted();
            } catch (final IOException e) {
                disconnect(client);
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io());
    }

    private void disconnect(FTPClient client) {
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (final IOException e1) {

        }
    }

    @Override
    public Observable<String> post(final InputStream is) {
        final String id = UUID.randomUUID().toString();
        return put(id, is).map(bb -> {
            if (!bb) {
                throw new RuntimeException("cannot post on:" + toString());
            }
            return id;
        });
    }

    @Override
    public Observable<Boolean> delete(final String pid) {
        return connect().flatMap(client -> Observable.<Boolean>create(subscriber -> {
            try {
                log.debug("delete {} {}", pid, this);
                final boolean deleteFile = client.deleteFile(pid);
                close(client);
                subscriber.onNext(deleteFile);
                subscriber.onCompleted();
            } catch (IOException e) {
                close(client);
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()));
    }

    @Override
    public Observable<Boolean> put(final String pid, final InputStream is) {
        return connect().flatMap(client -> Observable.<Boolean>create(subscriber -> {
            try {
                log.debug("put {} {}", pid, this);
                final boolean result = client.storeFile(pid, is);
                close(client);
                subscriber.onNext(result);
                subscriber.onCompleted();
            } catch (IOException e) {
                close(client);
                subscriber.onError(e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }).subscribeOn(Schedulers.io()));
    }

    @Override
    public Observable<InputStream> get(final String pid) {
        return connect().flatMap(client -> Observable.<InputStream>create(subscriber -> {
            try {
                log.debug("get {} {}", pid, this);
                InputStream is = client.retrieveFileStream(pid);
                if (is == null) {
                    subscriber.onError(new NotFoundException(pid));
                    return;
                }
                subscriber.onNext(is);
                subscriber.onCompleted();
            } catch (IOException e) {
                close(client);
                subscriber.onError(e);
            }
        }).subscribeOn(Schedulers.io()));
    }

    private void close(final FTPClient client) {
        try {
            client.logout();
        } catch (final IOException e1) {
        }
        try {
            client.disconnect();
        } catch (final IOException e1) {
        }
    }

    @Override
    public String providerId() {
        return String.format("ftp://%s@%s/%s", getConf().isAnonymous() ? "anonymous" : getConf().getUsername(), getConf().getHost(), defaultString(conf.getFolder()));
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                .append(providerId())
                .toString();
    }

    public FTPConf getConf() {
        return conf;
    }

    public void setConf(final FTPConf conf) {
        this.conf = conf;
    }
}
