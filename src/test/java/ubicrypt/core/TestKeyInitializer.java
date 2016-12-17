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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.dto.LocalConfig;

public class TestKeyInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  public static PGPKeyPair keyPair;
  public static PGPPrivateKey signKey;

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    final char[] password = "test".toCharArray();
    PGPSecretKeyRing kring = PGPEC.createSecretKeyRing(password);
    try {
      keyPair = PGPEC.extractEncryptKeyPair(kring, password);
      signKey = PGPEC.extractSignKey(kring, password);
    } catch (PGPException e) {
      Throwables.propagate(e);
    }
    applicationContext.getBeanFactory().registerSingleton("keyPair", keyPair);
    applicationContext.getBeanFactory().registerSingleton("signKey", signKey);
    applicationContext.getBeanFactory().registerSingleton("ubiqConfig", new LocalConfig());
  }
}
