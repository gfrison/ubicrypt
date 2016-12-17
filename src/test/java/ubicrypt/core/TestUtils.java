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
package ubicrypt.core;

import com.google.common.base.Throwables;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import ubicrypt.core.crypto.AESGCM;
import ubicrypt.core.dto.Key;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.file.FileConf;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.gdrive.GDriveAuthorizer;
import ubicrypt.core.provider.gdrive.GDriveConf;

import static org.slf4j.LoggerFactory.getLogger;

public class TestUtils {
  public static final Path tmp = Paths.get(System.getProperty("java.io.tmpdir")).resolve("ubiq");
  public static final Path tmp2 = Paths.get(System.getProperty("java.io.tmpdir")).resolve("ubiq2");
  private static final Logger log = getLogger(TestUtils.class);

  static {
    createDirs();
  }

  public static Path createRandomFile(Path path, Number size) {
    final byte[] bytes = new byte[size.intValue()];
    new Random().nextBytes(bytes);
    try {
      Files.createFile(path);
    } catch (IOException e) {
      Throwables.propagate(e);
    }
    try (FileOutputStream os = new FileOutputStream(path.toFile())) {
      os.write(bytes);
      return path;
    } catch (IOException e) {
      Throwables.propagate(e);
    }
    return null;
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
    return new FileProvider() {
      {
        setConf(
            new FileConf(path) {
              {
                setConfFile(
                    new RemoteFile() {
                      {
                        setRemoteName(RandomStringUtils.randomAlphabetic(6));
                        setKey(new Key(AESGCM.rndKey()));
                      }
                    });
                setLockFile(
                    new RemoteFile() {
                      {
                        setRemoteName(RandomStringUtils.randomAlphabetic(6));
                        setKey(new Key(AESGCM.rndKey()));
                      }
                    });
              }
            });
      }
    };
  }

  /**
   * Returns a free port number on localhost.
   *
   * <p>Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a dependency to JDT
   * just because of this). Slightly improved with stop() missing in JDT. And throws exception
   * instead of returning -1.
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

  public static GDriveConf createGDriveConf() throws GeneralSecurityException, IOException {
    final Path path = Paths.get(System.getProperty("user.home"), "gdrive");
    GDriveConf conf;
    try {
      conf = Utils.unmarshall(Files.newInputStream(path), GDriveConf.class);
      conf.setFolderName("test");
    } catch (Exception e) {
      log.info("conf not found:{}", e.getMessage(), e);
      GDriveAuthorizer autho = new GDriveAuthorizer();
      log.info("open url:{}", autho.authorizeUrl());
      conf = autho.credential();
      Utils.write(path, Utils.marshall(conf)).toBlocking().last();
    }
    return conf;
  }
}
