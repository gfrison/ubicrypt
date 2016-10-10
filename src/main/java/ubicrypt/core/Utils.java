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
package ubicrypt.core;

import com.google.common.base.Throwables;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import ubicrypt.UbiCrypt;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.util.PGPKValue;
import ubicrypt.core.util.PGPKValueDeserializer;
import ubicrypt.core.util.PGPKValueSerializer;
import ubicrypt.core.util.PathDeserializer;
import ubicrypt.core.util.PathSerializer;

import static org.apache.commons.lang3.StringUtils.substringAfter;

public class Utils {
    public static final Predicate<? super UbiFile> trackedFile = file -> !(file.isDeleted() || file.isRemoved());
    private final static Logger log = LoggerFactory.getLogger(Utils.class);
    public static final Action1<Throwable> logError = err -> log.error(err.getMessage(), err);
    private final static SmileFactory smile = new SmileFactory();
    private final static ObjectMapper mapper = new ObjectMapper(smile);

    static {
        configureMapper(mapper);
    }

    public static void configureMapper(final ObjectMapper mapper) {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new SimpleModule("ubicrypt module") {{
            addSerializer(new PGPKValueSerializer(PGPKValue.class));
            addDeserializer(PGPKValue.class, new PGPKValueDeserializer(PGPKValue.class));
            addSerializer(new PathSerializer(Path.class));
            addDeserializer(Path.class, new PathDeserializer(Path.class));
        }});
        mapper.registerModule(new AfterburnerModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static int deviceId() {
        try {
            final Enumeration<NetworkInterface> it = NetworkInterface.getNetworkInterfaces();
            int ret = 0;
            while (it.hasMoreElements() && ret == 0) {
                ret = Arrays.hashCode(it.nextElement().getHardwareAddress());
            }
            return ret;
        } catch (final SocketException e) {
            Throwables.propagate(e);
        }
        return -1;
    }

    public static Observable<Long> write(final Path target, final byte[] bytes) {
        return write(target, new ByteArrayInputStream(bytes));
    }

    private static void unsubscribe(final Subscriber<? super Long> subscriber, final InputStream is, final FileLock lock) {
        close(is, lock);
        if (!subscriber.isUnsubscribed()) {
            subscriber.onCompleted();
        }
    }

    private static void close(final InputStream is, final FileLock lock) {
        try {
            lock.release();
        } catch (final IOException e) {
        }
        try {
            is.close();
        } catch (final IOException e1) {

        }
    }

    public static Observable<Long> write(final Path fullPath, final InputStream inputStream) {
        return Observable.create(subscriber -> {
            try {
                final AtomicLong offset = new AtomicLong(0);
                final AsynchronousFileChannel afc = AsynchronousFileChannel.open(fullPath, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE);
                afc.lock(new Object(), new CompletionHandler<FileLock, Object>() {
                    @Override
                    public void completed(final FileLock lock, final Object attachment) {
                        //acquired lock
                        final byte[] buf = new byte[1 << 16];
                        try {
                            final int len = inputStream.read(buf);
                            if (len == -1) {
                                unsubscribe(subscriber, inputStream, lock);
                                return;
                            }
                            afc.write(ByteBuffer.wrap(Arrays.copyOfRange(buf, 0, len)), offset.get(), null, new CompletionHandler<Integer, Object>() {
                                @Override
                                public void completed(final Integer result, final Object attachment) {
                                    //written chunk of bytes
                                    subscriber.onNext(offset.addAndGet(result));
                                    final byte[] buf = new byte[1 << 16];
                                    int len;
                                    try {
                                        len = inputStream.read(buf);
                                        if (len == -1) {
                                            unsubscribe(subscriber, inputStream, lock);
                                            log.debug("written:{}", fullPath);
                                            return;
                                        }
                                    } catch (final IOException e) {
                                        subscriber.onError(e);
                                        return;
                                    }
                                    if (len == -1) {
                                        unsubscribe(subscriber, inputStream, lock);
                                        log.debug("written:{}", fullPath);
                                        return;
                                    }
                                    afc.write(ByteBuffer.wrap(Arrays.copyOfRange(buf, 0, len)), offset.get(), null, this);
                                }

                                @Override
                                public void failed(final Throwable exc, final Object attachment) {
                                    subscriber.onError(exc);
                                }
                            });
                        } catch (final Exception e) {
                            close(inputStream, lock);
                            subscriber.onError(e);
                        }
                    }

                    @Override
                    public void failed(final Throwable exc, final Object attachment) {
                        log.error("error on getting lock for:{}, error:{}", fullPath, exc.getMessage());
                        try {
                            inputStream.close();
                        } catch (final IOException e) {
                        }
                        subscriber.onError(exc);
                    }
                });

            } catch (final Exception e) {
                log.error("error on file:{}", fullPath);
                subscriber.onError(e);
            }
        });


    }

    static public InputStream readIs(final Path path) {
        final PipedInputStream pis = new PipedInputStream();
        final AtomicLong pos = new AtomicLong(0);
        try {
            final PipedOutputStream ostream = new PipedOutputStream(pis);
            final AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
            final ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
            channel.read(buffer, pos.get(), buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(final Integer result, final ByteBuffer buf) {
                    try {
                        if (result == -1) {
                            ostream.close();
                            return;
                        }
                        final byte[] bytes = new byte[result];
                        System.arraycopy(buf.array(), 0, bytes, 0, result);
                        ostream.write(bytes);
                        ostream.flush();
                        if (result < 1 << 16) {
                            ostream.close();
                            return;
                        }
                        pos.addAndGet(result);
                        final ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
                        channel.read(buffer, pos.get(), buffer, this);
                    } catch (final IOException e) {
                        Throwables.propagate(e);
                    }
                }

                @Override
                public void failed(final Throwable exc, final ByteBuffer attachment) {
                    log.error(exc.getMessage(), exc);
                }
            });
        } catch (final IOException e) {
            if (e instanceof NoSuchFileException) {
                throw new NotFoundException(path);
            }
            Throwables.propagate(e);
        }
        return pis;
    }

    public static InputStream convert(final Observable<byte[]> source) {
        final PipedOutputStream pos = new PipedOutputStream();
        try {
            final PipedInputStream pis = new PipedInputStream(pos);
            source.subscribe(bytes -> {
                try {
                    pos.write(bytes);
                    pos.flush();
                } catch (final IOException e) {
                    Throwables.propagate(e);
                }
            }, err -> {
                log.error(err.getMessage(), err);
                try {
                    pis.close();
                } catch (final IOException e) {
                }
            });
            return pis;
        } catch (final IOException e) {
            Throwables.propagate(e);
        }
        return null;
    }

    public static Observable<byte[]> read(final Path path) {
        return Observable.create(subscriber -> {
            final AtomicLong pos = new AtomicLong(0);
            try {
                final AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.READ);
                read(pos, ByteBuffer.allocate(1 << 16), channel, subscriber);
            } catch (final Throwable e) {
                subscriber.onError(e);
            }
        });
    }

    private static void read(final AtomicLong pos, final ByteBuffer buffer, final AsynchronousFileChannel channel,
                             final Subscriber<? super byte[]> subscriber) {
        channel.read(buffer, pos.get(), pos, new CompletionHandler<Integer, AtomicLong>() {
            @Override
            public void completed(final Integer result, final AtomicLong attachment) {
                if (result == -1) {
                    subscriber.onCompleted();
                    return;
                }
                subscriber.onNext(buffer.array());
                if (result < 1 << 16) {
                    subscriber.onCompleted();
                    return;
                }
                pos.addAndGet(result);
                read(pos, ByteBuffer.allocate(1 << 16), channel, subscriber);
            }

            @Override
            public void failed(final Throwable exc, final AtomicLong attachment) {
                subscriber.onError(exc);
            }
        });
    }

    public static Path ubiqFolder() {
        return Arrays.stream(UbiCrypt.arguments)
                .filter(arg -> arg.startsWith("--conf"))
                .map(conf -> substringAfter(conf, "="))
                .map(Paths::get)
                .findFirst()
                .orElseGet(() -> {
                    if ((System.getProperty("os.name")).toUpperCase().contains("WIN")) {
                        return Paths.get(System.getenv("AppData"), "ubicrypt");
                    } else {
                        return Paths.get(System.getProperty("user.home"), ".ubicrypt");
                    }
                });
    }

    public static boolean isAppInUse(final Path ubiqFolder) throws IOException {
        Files.createDirectories(ubiqFolder);
        final File file = Paths.get(ubiqFolder.toString(), "lock").toFile();
        final FileChannel channel = new RandomAccessFile(file, "rw").getChannel();

        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (final OverlappingFileLockException e) {
            return true;
        }
        if (lock == null) {
            return true;
        }
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    lock.release();
                    channel.close();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        });
        return false;
    }

    public static <T> T unmarshall(final InputStream is, final Class<T> remoteConfigClass) {
        try {
            return mapper.readValue(is, remoteConfigClass);
        } catch (final IOException e) {
            Throwables.propagate(e);
        }
        return null;
    }

    public static byte[] marshall(final Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (final IOException e) {
            Throwables.propagate(e);
            return null;
        }
    }

    public static InputStream marshallIs(final Object obj) {
        try {
            return new ByteArrayInputStream(mapper.writeValueAsBytes(obj));
        } catch (final IOException e) {
            Throwables.propagate(e);
            return null;
        }
    }

    public static <T> T unmarshall(final byte[] content, final Class<T> clz) {
        try {
            return mapper.readValue(content, clz);
        } catch (final IOException e) {
            log.debug("error unmarshall:{}", new String(content));
            Throwables.propagate(e);
        }
        return null;
    }

    private static Path getLocalConfigFile(final String file) {
        return ubiqFolder().resolve(file);
    }

    public static Path securityFile() {
        return getLocalConfigFile("sec");
    }

    public static Path ownPKRingFile() {
        return getLocalConfigFile("mypks");
    }

    public static Path configFile() {
        return getLocalConfigFile("config");
    }

    public static PGPKeyPair readPrivateKey(final char[] password) throws PGPException {
        try {
            return PGPEC.extractEncryptKeyPair(PGPEC.readSK(Files.newInputStream(Utils.securityFile())), password);
        } catch (final PGPException e) {
            throw e;
        } catch (final IOException e) {
            Throwables.propagate(e);
        }
        return null;
    }

    public static void close(final Closeable... closeables) {
        for (final Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (final Exception e) {
            }
        }
    }

    public static <T> Stream<T> toStream(final Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }

    public static String machineName() {
        try {
            return System.getProperty("user.name") + "/" + InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            log.error(e.getMessage(), e);
        }
        return System.getProperty("user.name");
    }

    public static <T> Set<T> copySynchronized(final Set<T> set) {
        final Set<T> ret = ConcurrentHashMap.newKeySet();
        ret.addAll(set);
        return ret;
    }

    public static <T> T springIt(ConfigurableApplicationContext ctx, T object) {
        return springIt(ctx, object, object.getClass().getName());
    }

    public static <T> T springIt(ConfigurableApplicationContext ctx, T object, String name) {
        final AutowireCapableBeanFactory factory = ctx.getAutowireCapableBeanFactory();
        factory.autowireBean(object);
        factory.applyBeanPostProcessorsBeforeInitialization(object, name);
        factory.initializeBean(object, name);
        factory.applyBeanPostProcessorsAfterInitialization(object, name);
        return object;
    }

    public static Observable.OnSubscribe<Boolean> emptySubject() {
        return subscriber -> subscriber.onCompleted();
    }
}
