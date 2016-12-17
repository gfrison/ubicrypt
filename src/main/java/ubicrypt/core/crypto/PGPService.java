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
package ubicrypt.core.crypto;

import com.google.common.base.Throwables;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.InputStream;
import java.util.stream.Collectors;

import javax.inject.Inject;

import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.util.EqualsValue;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.slf4j.LoggerFactory.getLogger;

public class PGPService implements IPGPService {
  private static final Logger log = getLogger(PGPService.class);

  @Autowired
  @Qualifier("keyPair")
  PGPKeyPair keyPair;

  @Inject
  LocalConfig localConfig;

  public PGPService() {
  }

  public PGPService(final PGPKeyPair keyPair, final LocalConfig localConfig) {
    this.keyPair = keyPair;
    this.localConfig = localConfig;
  }

  @Override
  public InputStream encrypt(final InputStream clearBytes) {
    checkNotNull(keyPair, "keyPair must not be null");
    checkNotNull(localConfig, "localConfig must not be null");
    return PGPEC.encrypt(
      concat(
        of(keyPair.getPublicKey()),
        localConfig.getOwnedPKs().stream().map(EqualsValue::getValue))
        .collect(Collectors.toList()),
      clearBytes);
  }

  @Override
  public InputStream decrypt(final InputStream cipherText) {
    try {
      return PGPEC.decrypt(keyPair.getPrivateKey(), cipherText);
    } catch (final PGPException e) {
      Throwables.propagate(e);
    }
    return null;
  }

  @Override
  public long keyId() {
    return keyPair.getPublicKey().getKeyID();
  }
}
