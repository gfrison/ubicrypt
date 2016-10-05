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
package ubicrypt.core.watch;

import com.google.common.base.Throwables;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.inject.Inject;

import rx.subjects.PublishSubject;
import rx.subjects.Subject;
import ubicrypt.core.Utils;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class WatcherBroadcaster {
    private final static Logger log = LoggerFactory.getLogger(WatcherBroadcaster.class);
    private final AtomicBoolean filesChanging = new AtomicBoolean(false);
    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(10);
    private final Path basePath;
    private final Map<Path, WatchService> watchers = new ConcurrentHashMap<>();
    private final Consumer<Path> register = (dir) -> {
        try {
            if (!watchers.containsKey(dir)) {
                log.info("watching folder:{}", dir);
                final WatchService watchService = FileSystems.getDefault().newWatchService();
                watchers.put(dir, watchService);
                dir.register(watchService, ENTRY_DELETE, ENTRY_MODIFY, ENTRY_CREATE);
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    };
    private final HashMap<Path, Long> fileLastmodified = new HashMap<>();
    private final List<Path> deletes = new CopyOnWriteArrayList();
    @Inject
    LocalConfig localConfig;
    @Inject
    PublishSubject<PathEvent> pathStream;
    @Resource
    @Qualifier("synchProcessing")
    Subject<Boolean, Boolean> synchProcessing;
    private boolean active = true;

    public WatcherBroadcaster(final Path basePath) {
        this.basePath = basePath;
    }

    /**
     * register parent folder's file
     */
    private void registerFileFolders() {
        localConfig.getLocalFiles().stream()
                .filter(Utils.trackedFile)
                .map(LocalFile::getPath)
                .map(basePath::resolve)
                .filter(Files::isRegularFile)
                .map(path -> path.toFile().getParentFile().toPath())
                .distinct()
                .forEach(register);
    }

    /**
     * register entire folder
     */
    private void registerTrackedFolders() {
        localConfig.getTrackedFolders().stream()
                .map(basePath::resolve)
                .filter(Files::isDirectory)
                .distinct()
                .forEach(register);
    }

    @PostConstruct
    public void init() throws IOException {
        log.info("watching for tracked files, basePath:{}", basePath);
        registerFileFolders();
        registerTrackedFolders();
        executorService.execute(createWatcher());
        synchProcessing.subscribe(filesChanging::set);
    }

    private Runnable createWatcher() {
        return () -> {
            while (active) {
                try {
                    watchers.entrySet().forEach(entry -> {
                        final WatchKey key = entry.getValue().poll();
                        if (key == null) {
                            return;
                        }
                        for (final WatchEvent event : key.pollEvents()) {
                            if (filesChanging.get()) {
                                continue;
                            }
                            final WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            final Path path = basePath.relativize(entry.getKey().resolve(ev.context()));
                            if (ev.kind() != ENTRY_DELETE && isDoubleEvent(path)) {
                                continue;
                            }
                            log.debug("watcher event kind:{}, path:{}", ev.kind(), path);
                            if (ev.kind() == ENTRY_CREATE) {
                                filterDeleteThenCreate(PathEvent.Event.create, path);
                            } else if (ev.kind() == ENTRY_DELETE) {
                                filterDeleteThenCreate(PathEvent.Event.delete, path);
                            } else if (ev.kind() == ENTRY_MODIFY) {
                                filterDeleteThenCreate(PathEvent.Event.update, path);
                            }
                        }
                        if (!key.reset()) {
                            log.error("watcher key not valid, quit");
                        }
                    });
                } catch (final Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        };
    }

    void filterDeleteThenCreate(final PathEvent.Event event, final Path path) {
        if (event == PathEvent.Event.delete) {
            deletes.add(path);
            executorService.schedule(() -> {
                if (!deletes.contains(path)) {
                    dispatch(PathEvent.Event.update, path);
                    return;
                }
                deletes.remove(path);
                dispatch(PathEvent.Event.delete, path);
            }, 10, TimeUnit.MILLISECONDS);
        } else {
            if (!deletes.contains(path)) {
                dispatch(event, path);
                return;
            }
            deletes.remove(path);
        }
    }

    void dispatch(final PathEvent.Event event, final Path path) {
        if (localConfig.getTrackedFolders().stream()
                .filter(path::startsWith)
                .findFirst().isPresent()) {
            final Path resolve = basePath.resolve(path);
            if (Files.isDirectory(resolve)) {
                register.accept(resolve);
                return;
            }
            pathStream.onNext(new PathEvent(event, resolve));
            return;
        }
        if (localConfig.getLocalFiles().stream()
                .filter(file -> file.getPath().equals(path))
                .findFirst().isPresent()) {
            pathStream.onNext(new PathEvent(event, basePath.resolve(path)));
        }
    }

    public void watchPath(final Path absolutePath) {
        if (Files.notExists(absolutePath)) {
            return;
        }
        if (Files.isDirectory(absolutePath)) {
            register.accept(absolutePath);
        } else {
            register.accept(absolutePath.toFile().getParentFile().toPath());
        }
    }

    private boolean isDoubleEvent(final Path path) {
        long milli = -1;
        try {
            milli = Files.getLastModifiedTime(basePath.resolve(path)).toInstant().toEpochMilli();
        } catch (final NoSuchFileException e) {
            log.debug("deleted file:{}", path);
            return true;
        } catch (final IOException e) {
            Throwables.propagate(e);
        }
        final boolean ret = fileLastmodified.containsKey(path)
                && fileLastmodified.get(path) == milli;
        fileLastmodified.put(path, milli);
        return ret;
    }

    @PreDestroy
    public void close() {
        active = false;
        executorService.shutdownNow();
    }

    public Path getBasePath() {
        return basePath;
    }

}
