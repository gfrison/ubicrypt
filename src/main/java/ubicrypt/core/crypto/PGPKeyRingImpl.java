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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

class PGPKeyRingImpl {
  protected static final Log log = LogFactory.getLog(PGPKeyRingImpl.class);

  private String publicKeyRingFileName;

  private HashMap<String, PGPPublicKey> principalsKeyBundleMap;

  private String secretKeyRingFileName;

  private String secretAliasId;

  private PGPSecretKey secretKey;

  private String secretPassphrase;

  public void initialise() throws Exception {
    java.security.Security.addProvider(new BouncyCastleProvider());

    principalsKeyBundleMap = new HashMap<>();

    readPublicKeyRing();
    readPrivateKeyBundle();
  }

  private void readPublicKeyRing() throws Exception {
    final InputStream in = new FileInputStream(getPublicKeyRingFileName());
    final PGPPublicKeyRingCollection collection =
        new PGPPublicKeyRingCollection(in, new BcKeyFingerprintCalculator());
    in.close();

    for (final Iterator iterator = collection.getKeyRings(); iterator.hasNext(); ) {
      final PGPPublicKeyRing ring = (PGPPublicKeyRing) iterator.next();
      String userID = "";
      for (final Iterator iterator2 = ring.getPublicKeys(); iterator2.hasNext(); ) {
        final PGPPublicKey publicKey = (PGPPublicKey) iterator2.next();
        final Iterator userIDs = publicKey.getUserIDs();
        if (userIDs.hasNext()) {
          userID = (String) userIDs.next();
        }

        principalsKeyBundleMap.put(userID, publicKey);
      }
    }
  }

  private void readPrivateKeyBundle() throws Exception {
    final InputStream in = new FileInputStream(getSecretKeyRingFileName());
    final PGPSecretKeyRingCollection collection =
        new PGPSecretKeyRingCollection(in, new BcKeyFingerprintCalculator());
    in.close();
    final Iterator iter = collection.getKeyRings();
    while (iter.hasNext()) {
      final PGPSecretKeyRing sec = (PGPSecretKeyRing) iter.next();
      final Iterator userids = sec.getPublicKey().getUserIDs();
      while (userids.hasNext()) {
        final String uid = (String) userids.next();
      }
    }
    secretKey = collection.getSecretKey(Long.valueOf(getSecretAliasId()));

    if (secretKey == null) {
      final StringBuilder message = new StringBuilder();
      message.append('\n');
      final Iterator iterator = collection.getKeyRings();
      while (iterator.hasNext()) {
        final PGPSecretKeyRing ring = (PGPSecretKeyRing) iterator.next();
        final Iterator secretKeysIterator = ring.getSecretKeys();
        while (secretKeysIterator.hasNext()) {
          final PGPSecretKey k = (PGPSecretKey) secretKeysIterator.next();
          message.append("Key: ");
          message.append(k.getKeyID());
          message.append('\n');
        }
      }
      throw new Exception("no secret found but available:" + message.toString());
    }
  }

  private String getSecretKeyRingFileName() {
    return secretKeyRingFileName;
  }

  public void setSecretKeyRingFileName(final String value) {
    this.secretKeyRingFileName = value;
  }

  private String getSecretAliasId() {
    return secretAliasId;
  }

  public void setSecretAliasId(final String value) {
    this.secretAliasId = value;
  }

  public String getSecretPassphrase() {
    return secretPassphrase;
  }

  public void setSecretPassphrase(final String value) {
    this.secretPassphrase = value;
  }

  public PGPSecretKey getSecretKey() {
    return secretKey;
  }

  private String getPublicKeyRingFileName() {
    return publicKeyRingFileName;
  }

  public void setPublicKeyRingFileName(final String value) {
    this.publicKeyRingFileName = value;
  }

  public PGPPublicKey getPublicKey(final String principalId) {
    return principalsKeyBundleMap.get(principalId);
  }
}
