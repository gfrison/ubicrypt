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

import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.file.FileConf;
import ubicrypt.core.provider.file.FileProvider;

public class TestUtils {
    public static final Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).resolve("ubiq");
    public static final Path tmp2 = Paths.get(System.getProperty("java.io.tmpdir")).resolve("ubiq2");

    static {
        createDirs();
    }

    public static void createDirs() {
        try {
            Files.createDirectories(tmp);
            Files.createDirectories(tmp2);
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteDirs() {
        deleteR(tmp);
        deleteR(tmp2);
    }

    public static void deleteR(final Path path) {
        try {
            if (!Files.isDirectory(path)) {
                Files.deleteIfExists(path);
                return;
            }
            Files.list(path).forEach(TestUtils::deleteR);
            Files.deleteIfExists(path);
        } catch (final IOException e) {
            Throwables.propagate(e);
        }
    }

    public static FileProvider fileProvider(final Path path) {
        return new FileProvider() {{
            setConf(new FileConf(path) {{
                setConfFile(new RemoteFile() {{
                    setRemoteName(RandomStringUtils.randomAlphabetic(6));
                    setKey(new Key(AESGCM.rndKey()));
                }});
                setLockFile(new RemoteFile() {{
                    setRemoteName(RandomStringUtils.randomAlphabetic(6));
                    setKey(new Key(AESGCM.rndKey()));
                }});
            }});
        }};
    }

    /**
     * Returns a free port number on localhost.
     *
     * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a dependency to JDT just
     * because of this). Slightly improved with stop() missing in JDT. And throws exception instead
     * of returning -1.
     *
     * @return a free port number on localhost
     * @throws IllegalStateException if unable to find a free port
     */
    public static int findFreePort() {
        ServerSocket socket = null;
        do {
            try {
                final int port = ThreadLocalRandom.current().nextInt(49152, 65535 + 1);
                socket = new ServerSocket(port);
                socket.setReuseAddress(false);
                try {
                    socket.close();
                } catch (final IOException e) {
                    // Ignore IOException on stop()
                }
                return port;
            } catch (final IOException e) {
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (final IOException e) {
                    }
                }
            }
        } while (true);
    }
}
