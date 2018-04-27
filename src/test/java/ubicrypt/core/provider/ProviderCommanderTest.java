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
package ubicrypt.core.provider;

import org.junit.Ignore;
import org.junit.Test;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.ftp.FTPConf;
import ubicrypt.core.provider.ftp.FTProvider;
import ubicrypt.core.remote.RemoteConfig;
import ubicrypt.core.remote.RemoteRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@Ignore
public class ProviderCommanderTest {

  @Test
  public void duplicatedProvider() throws Exception {
    final ProviderCommander pc = new ProviderCommander();
    pc.localConfig = new LocalConfig();
    final FTPConf con = new FTPConf();
    con.setHost("host");
    con.setAnonymous(true);
    final FTProvider ftp = new FTProvider();
    ftp.setConf(con);
    pc.localConfig.getProviders().add(ftp);
    final FTProvider provider = new FTProvider();
    final FTPConf conf = new FTPConf();
    conf.setHost("host");
    conf.setAnonymous(true);
    provider.setConf(conf);

    assertThat(pc.register(provider).toBlocking().first()).isEqualTo(false);
  }

  @Test(expected = NoSuchElementException.class)
  public void empty() throws Exception {
    final ProviderCommander pc = new ProviderCommander();
    final ProviderHook mock1 = mock(ProviderHook.class);
    doReturn(Optional.empty()).when(mock1).getConfig();
  }

  @Test
  public void testName() throws Exception {
    final ProviderCommander pc = new ProviderCommander();
    final UUID uuid = UUID.randomUUID();
    final ProviderHook mock1 = mock(ProviderHook.class);
    final ProviderHook mock2 = mock(ProviderHook.class);
    doReturn(
            Optional.of(
                new RemoteConfig() {
                  @Override
                  public Set<RemoteFile> getRemoteFiles() {
                    return new HashSet<RemoteFile>() {
                      {
                        add(
                            new RemoteFile() {
                              {
                                setId(uuid);
                                setRemoteName("newer");
                                getVclock().increment(1);
                                getVclock().increment(1);
                              }
                            });
                      }
                    };
                  }
                }))
        .when(mock1)
        .getConfig();
    final RemoteRepository origin = mock(RemoteRepository.class);
    doReturn(origin).when(mock1).getRepository();
    doReturn(
            Optional.of(
                new RemoteConfig() {
                  @Override
                  public Set<RemoteFile> getRemoteFiles() {
                    return new HashSet<RemoteFile>() {
                      {
                        add(
                            new RemoteFile() {
                              {
                                setId(uuid);
                                setRemoteName("older");
                                getVclock().increment(1);
                              }
                            });
                      }
                    };
                  }

                  //            @Override
                  public ProviderHook getHook() {
                    return mock2;
                  }
                }))
        .when(mock2)
        .getConfig();
  }
}
