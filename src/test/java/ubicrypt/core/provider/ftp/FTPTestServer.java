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
package ubicrypt.core.provider.ftp;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

public class FTPTestServer {
  private static final Logger log = getLogger(FTPTestServer.class);

  private String username;
  private String password;
  private boolean anonymous = true;
  private int port = 21;
  private Path home = Paths.get(System.getProperty("java.io.tmpdir"));

  public FTPTestServer() {}

  private FTPTestServer(final FTPTestServer testServer) {
    this.username = testServer.username;
    this.password = testServer.password;
    this.anonymous = testServer.anonymous;
    this.port = testServer.port;
    this.home = testServer.home;
  }

  public FTPTestServer username(final String username) {
    final FTPTestServer ret = new FTPTestServer(this);
    ret.username = username;
    return ret;
  }

  public FTPTestServer password(final String password) {
    final FTPTestServer ret = new FTPTestServer(this);
    ret.password = password;
    return ret;
  }

  public FTPTestServer anonymous(final boolean anonymous) {
    final FTPTestServer ret = new FTPTestServer(this);
    ret.anonymous = anonymous;
    return ret;
  }

  public FTPTestServer port(final int port) {
    final FTPTestServer ret = new FTPTestServer(this);
    ret.port = port;
    return ret;
  }

  public FTPTestServer home(final Path home) {
    final FTPTestServer ret = new FTPTestServer(this);
    ret.home = home;
    return ret;
  }

  public FtpServer start() throws FtpException {
    if (!anonymous) {
      checkNotNull(username);
      checkNotNull(password);
    }
    final FtpServerFactory factory = new FtpServerFactory();
    if (anonymous) {
      final ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
      connectionConfigFactory.setAnonymousLoginEnabled(false);
      factory.setConnectionConfig(connectionConfigFactory.createConnectionConfig());
    }

    final PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();

    final UserManager userManager = userManagerFactory.createUserManager();
    final BaseUser user = new BaseUser();
    if (!anonymous) {
      user.setName(username);
      user.setPassword(password);
    }
    user.setHomeDirectory(home.toString());
    final List<Authority> authorities = new ArrayList<>();
    authorities.add(new WritePermission());
    user.setAuthorities(authorities);
    userManager.save(user);

    final ListenerFactory listenerFactory = new ListenerFactory();
    listenerFactory.setPort(port);

    factory.setUserManager(userManager);
    factory.addListener("default", listenerFactory.createListener());

    final FtpServer server = factory.createServer();
    server.start();
    log.info("ftp server started, port:{}, home:{}, anonymous:{}", port, home, anonymous);
    return server;
  }
}
