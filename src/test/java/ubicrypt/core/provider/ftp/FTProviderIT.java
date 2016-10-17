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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ftpserver.FtpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import ubicrypt.core.TestUtils;
import ubicrypt.core.provider.ProviderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.slf4j.LoggerFactory.getLogger;

public class FTProviderIT {
    private static final Logger log = getLogger(FTProviderIT.class);

    private FtpServer server;
    private int port;

    @Before
    public void setUp() throws Exception {
        this.port = TestUtils.findFreePort();
        log.debug("start ftp server port:{}", port);
        TestUtils.deleteDirs();
        TestUtils.createDirs();
        server = new FTPTestServer()
                .anonymous(false)
                .username("user")
                .password("pwd")
                .home(TestUtils.tmp)
                .port(port)
                .start();

    }

    @After
    public void tearDown() throws Exception {
        server.stop();

    }

    @Test
    public void test() throws Exception {
        Thread.sleep(100);
        final FTProvider ftp = new FTProvider();
        ftp.setConf(new FTPConf() {{
            setHost("localhost");
            setUsername("user");
            setPassword("pwd".toCharArray());
            setPort(port);
        }});
        assertions(ftp);
    }

    public void assertions(FTProvider ftp) throws IOException {
        assertThat(ftp.init(1).toBlocking().last()).isEqualTo(ProviderStatus.initialized);
        final String pid = ftp.post(new ByteArrayInputStream(StringUtils.repeat("ciao", 99999).getBytes())).toBlocking().last();
        assertThat(pid).isNotNull();

        assertThat(IOUtils.toByteArray(ftp.get(pid).toBlocking().last())).isEqualTo(StringUtils.repeat("ciao", 99999).getBytes());

        assertThat(ftp.put(pid, new ByteArrayInputStream("ciao2".getBytes())).toBlocking().last()).isEqualTo(true);
        assertThat(IOUtils.readLines(ftp.get(pid).toBlocking().last())).contains("ciao2");

        assertThat(ftp.delete(pid).toBlocking().last()).isTrue();
        assertThatThrownBy(() -> IOUtils.readLines(ftp.get(pid).toBlocking().first()));
    }

    @Test
    public void folder() throws Exception {
        Thread.sleep(100);
        final FTProvider ftp = new FTProvider();
        ftp.setConf(new FTPConf() {{
            setHost("localhost");
            setUsername("user");
            setPassword("pwd".toCharArray());
            setFolder("ciao");
            setPort(port);
        }});
        assertions(ftp);
    }
}
