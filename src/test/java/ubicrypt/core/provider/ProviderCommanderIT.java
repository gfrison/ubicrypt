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

import com.google.common.collect.Sets;

import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;
import javax.inject.Inject;

import ubicrypt.core.FileCommander;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.local.LocalRepository;
import ubicrypt.core.provider.file.FileConf;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.util.EqualsValue;
import ubicrypt.core.util.ObjectSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.TestUtils.fileProvider;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
  loader = AnnotationConfigContextLoader.class,
  classes = {ProviderCommanderIT.Config.class}
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProviderCommanderIT {

  private final Path provider2 = TestUtils.tmp.resolve(RandomStringUtils.randomAlphabetic(3));
  private final Path provider3 = TestUtils.tmp.resolve(RandomStringUtils.randomAlphabetic(3));
  @Inject ProviderCommander pc;
  @Resource List<ProviderHook> providers;
  @Inject FileCommander fc;
  @Inject LocalConfig localConfig;

  @Before
  public void setUp() throws Exception {
    TestUtils.createDirs();
    Files.createDirectories(provider2);
    Files.createDirectories(provider3);
  }

  @After
  public void tearDown() throws Exception {
    TestUtils.deleteDirs();
    TestUtils.deleteR(provider2);
    TestUtils.deleteR(provider3);
  }

  @Test(expected = NotFoundException.class)
  public void removeProviderNotFound() throws Exception {
    pc.remove(fileProvider(provider2)).toBlocking().last();
  }

  @Test
  public void removeProviderSingle() throws Exception {
    final FileProvider provider = fileProvider(provider2);
    assertThat(pc.register(provider).toBlocking().last()).isTrue();
    assertThat(pc.remove(provider).toBlocking().last()).isTrue();
    assertThat(localConfig.getProviders()).hasSize(0);
  }

  @Test
  public void removeProviderMulti() throws Exception {
    final FileProvider provider = fileProvider(provider2);
    final FileProvider provider2 = fileProvider(provider3);
    assertThat(pc.register(provider).toBlocking().last()).isTrue();
    assertThat(pc.register(provider2).toBlocking().last()).isTrue();
    assertThat(pc.remove(provider).toBlocking().last()).isTrue();
    //        assertThat(providers.get(0).getConfig().get().getProviders()).hasSize(0);
    assertThat(localConfig.getProviders()).hasSize(1);
  }

  @Test
  public void addProvider() throws Exception {
    pc.register(fileProvider(provider2)).toBlocking().last();
    assertThat(providers).hasSize(1);
  }

  @Test
  public void add2Providers() throws Exception {
    addProvider();
    final FileProvider provider = fileProvider(provider3);
    pc.register(provider).toBlocking().last();
    assertThat(providers).hasSize(2);
    //test if the other remote provider conf, contains the provider
    //        assertThat(providers.stream().filter(hook -> hook.getConfig().get().getProviders().size() == 1)).hasSize(2);
  }

  @Test
  public void addWithFile() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    fc.addFile(TestUtils.tmp.resolve("origin"))
        .toBlocking()
        .last()
        .getT2()
        .toBlocking()
        .lastOrDefault(true);
    assertThat(localConfig.getLocalFiles()).hasSize(1);
    final FileProvider provider = fileProvider(provider2);
    pc.register(provider).toBlocking().last();
  }

  @Test
  public void addWithRemovedFile() throws Exception {
    Utils.write(TestUtils.tmp.resolve("origin"), "ciao".getBytes()).toBlocking().last();
    fc.addFile(TestUtils.tmp.resolve("origin"))
        .toBlocking()
        .last()
        .getT2()
        .toBlocking()
        .lastOrDefault(true);
    fc.removeFile(TestUtils.tmp.resolve("origin")).toBlocking().lastOrDefault(true);
    assertThat(localConfig.getLocalFiles()).hasSize(1);
    final FileProvider provider = fileProvider(provider2);
    pc.register(provider).toBlocking().last();
  }

  @Test
  public void ownedKeys() throws Exception {
    final PGPKeyPair nk = PGPEC.encryptionKey();
    final FileProvider fp = new FileProvider();
    final FileConf fconf = new FileConf();
    fconf.setPath(TestUtils.tmp);
    fp.setConf(fconf);
    assertThat(pc.register(fp).toBlocking().first()).isTrue();

    assertThat(pc.addOwnedPK(nk.getPublicKey()).toBlocking().first()).isTrue();
    final ObjectSerializer os = new ObjectSerializer(fp);
    os.setPgpService(new PGPService(nk, localConfig));
    assertThat(os.getObject(fp.getConfFile(), RemoteConfig.class).toBlocking().first()).isNotNull();
  }

  @Configuration
  @Import(RemoteCtxConf.class)
  public static class Config {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertyConfigIn() {
      return new PropertySourcesPlaceholderConfigurer();
    }

    @Bean
    public List<ProviderHook> providers() {
      return new ArrayList<>();
    }

    @Bean
    public LocalConfig localConfig() {
      return new LocalConfig();
    }

    @Bean
    public LocalRepository localRepository() throws IOException {
      return new LocalRepository(TestUtils.tmp);
    }

    @Bean
    public ProviderCommander providerCommander() {
      return new ProviderCommander();
    }

    @Bean
    public FileCommander fileCommander() {
      return new FileCommander(TestUtils.tmp);
    }

    @Bean
    public PGPService pgpService() {
      return new PGPService();
    }

    @Bean
    PGPKeyPair keyPair() {
      return PGPEC.encryptionKey();
    }

    @Bean
    public Set<EqualsValue<PGPPublicKey>> ownedPKs(final PGPKeyPair keyPair) {
      return Sets.newHashSet(new EqualsValue<>(keyPair.getPublicKey(), PGPPublicKey::getKeyID));
    }

    @Bean
    public int deviceId() {
      return Utils.deviceId();
    }
  }
}
