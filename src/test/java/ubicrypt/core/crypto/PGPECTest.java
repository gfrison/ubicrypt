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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import ubicrypt.core.Utils;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static ubicrypt.core.crypto.PGPEC.decrypt;
import static ubicrypt.core.crypto.PGPEC.encrypt;
import static ubicrypt.core.crypto.PGPEC.encryptionKey;

public class PGPECTest {
  @Test
  public void encDecCompressed() throws Exception {
    long ts = System.currentTimeMillis();
    final PGPKeyPair kp = encryptionKey();
    ts = System.currentTimeMillis();
    final String str = "ciaoo";
    final InputStream encrypt =
        encrypt(
            Collections.singletonList(kp.getPublicKey()),
            new ByteArrayInputStream(StringUtils.repeat(str, 1).getBytes()));
    ts = System.currentTimeMillis();
    final InputStream is = decrypt(kp.getPrivateKey(), encrypt);
    ts = System.currentTimeMillis();
    assertThat(new String(IOUtils.toByteArray(is))).isEqualTo(str);
  }

  @Test
  public void encDec() throws Exception {
    long ts = System.currentTimeMillis();
    final PGPKeyPair kp = encryptionKey();
    ts = System.currentTimeMillis();
    final String str = StringUtils.repeat("ciaoo", 1000);
    final InputStream encrypt =
        encrypt(
            Collections.singletonList(kp.getPublicKey()), new ByteArrayInputStream(str.getBytes()));
    ts = System.currentTimeMillis();
    final InputStream is = decrypt(kp.getPrivateKey(), encrypt);
    ts = System.currentTimeMillis();
    assertThat(new String(IOUtils.toByteArray(is))).isEqualTo(str);
  }

  @Test
  public void serializeKey() throws Exception {
    final char[] passPhrase = "pass".toCharArray();
    final PGPSecretKeyRing secr = PGPEC.createSecretKeyRing(passPhrase);
    final PGPKeyPair keyPair = PGPEC.extractEncryptKeyPair(secr, passPhrase);
    final byte[] secbytes = secr.getEncoded();
    final InputStream encbody =
        PGPEC.encrypt(
            Collections.singletonList(keyPair.getPublicKey()),
            new ByteArrayInputStream("ciao".getBytes()));
    final PGPSecretKeyRing secring = PGPEC.readSK(secbytes);
    assertThat(secbytes).isEqualTo(secring.getEncoded());
    final byte[] clearBytes = "ciaoo".getBytes();
    final PGPKeyPair privateKey = PGPEC.extractEncryptKeyPair(secring, passPhrase);
    assertThat(
            IOUtils.toByteArray(
                decrypt(
                    privateKey.getPrivateKey(),
                    encrypt(
                        Collections.singletonList(privateKey.getPublicKey()),
                        new ByteArrayInputStream(clearBytes)))))
        .isEqualTo(clearBytes);
    assertThat(IOUtils.toByteArray(decrypt(privateKey.getPrivateKey(), encbody)))
        .isEqualTo("ciao".getBytes());
  }

  @Test
  public void serializePKring() throws Exception {
    final PGPKeyPair sign = PGPEC.masterKey();
    final PGPKeyPair enc = PGPEC.encryptionKey();

    final char[] passPhrase = "g".toCharArray();
    final PGPKeyPair newKeyPair =
        PGPEC.extractEncryptKeyPair(PGPEC.createSecretKeyRing(passPhrase), passPhrase);
    final PGPKeyPair newKeyPair2 =
        PGPEC.extractEncryptKeyPair(PGPEC.createSecretKeyRing(passPhrase), passPhrase);

    final PGPPublicKeyRing kring =
        PGPPublicKeyRing.insertPublicKey(
            PGPEC.keyRingGenerator(sign).generatePublicKeyRing(), newKeyPair.getPublicKey());

    List<PGPPublicKey> pks = PGPEC.readPKring(new ByteArrayInputStream(kring.getEncoded()));

    assertThat(pks).hasSize(1);
    assertThat(pks.get(0).getKeyID()).isEqualTo(newKeyPair.getKeyID());

    pks =
        PGPEC.readPKring(
            new ByteArrayInputStream(
                PGPPublicKeyRing.insertPublicKey(kring, newKeyPair2.getPublicKey()).getEncoded()));
    assertThat(pks).hasSize(2);
    assertThat(pks.stream().map(PGPPublicKey::getKeyID).collect(Collectors.toList()))
        .contains(newKeyPair.getKeyID(), newKeyPair2.getKeyID());
  }

  @Test
  public void signPK() throws Exception {
    final char[] password = "ciao".toCharArray();
    final PGPSecretKeyRing skr = PGPEC.createSecretKeyRing(password);
    final byte[] encSkr = skr.getEncoded();
    final PGPKeyPair keyPair =
        PGPEC.extractEncryptKeyPair(
            PGPEC.readSK(new ByteArrayInputStream(encSkr)), "ciao".toCharArray());
    final PGPKeyRingGenerator pkgen = PGPEC.keyRingGenerator();

    final PGPSecretKeyRing targetSecRing = PGPEC.createSecretKeyRing("g".toCharArray());
    final PGPPrivateKey priv = PGPEC.extractSignKey(targetSecRing, "g".toCharArray());
    final PGPPublicKeyRing pkr =
        PGPPublicKeyRing.insertPublicKey(
            pkgen.generatePublicKeyRing(), PGPEC.signPK(keyPair.getPublicKey(), priv));

    final byte[] pkis = pkr.getEncoded();

    final List<PGPPublicKey> loadRing = PGPEC.readPKring(new ByteArrayInputStream(pkis));
    assertThat(loadRing).hasSize(1);
    assertThat(
            Utils.toStream(loadRing.get(0).getKeySignatures())
                .filter(sig -> ((PGPSignature) sig).getKeyID() == priv.getKeyID())
                .findFirst())
        .isPresent();
  }

  @Test
  public void encIs() throws Exception {
    final PGPKeyPair keypair = PGPEC.encryptionKey();
    final InputStream encIs =
        PGPEC.encrypt(
            Collections.singletonList(keypair.getPublicKey()),
            new ByteArrayInputStream("ciao".getBytes()));
    assertThat(IOUtils.toByteArray(PGPEC.decrypt(keypair.getPrivateKey(), encIs)))
        .isEqualTo("ciao".getBytes());
  }

  @Test
  public void encIsBig() throws Exception {
    final String big = StringUtils.repeat('a', 2 << 16);
    final PGPKeyPair keypair = PGPEC.encryptionKey();
    final InputStream encIs =
        PGPEC.encrypt(
            Collections.singletonList(keypair.getPublicKey()),
            new ByteArrayInputStream(big.getBytes()));
    assertThat(IOUtils.toByteArray(PGPEC.decrypt(keypair.getPrivateKey(), encIs)))
        .isEqualTo(big.getBytes());
  }

  @Test
  public void encMultiKeys() throws Exception {
    final PGPKeyPair kp1 = PGPEC.encryptionKey();
    final PGPKeyPair kp2 = PGPEC.encryptionKey();
    final PGPKeyPair kp3 = PGPEC.encryptionKey();
    InputStream encis =
        PGPEC.encrypt(
            asList(kp1.getPublicKey(), kp2.getPublicKey()),
            new ByteArrayInputStream("ciao".getBytes()));
    assertThat(IOUtils.toByteArray(PGPEC.decrypt(kp1.getPrivateKey(), encis)))
        .isEqualTo("ciao".getBytes());
    encis =
        PGPEC.encrypt(
            asList(kp1.getPublicKey(), kp2.getPublicKey()),
            new ByteArrayInputStream("ciao".getBytes()));
    assertThat(IOUtils.toByteArray(PGPEC.decrypt(kp2.getPrivateKey(), encis)))
        .isEqualTo("ciao".getBytes());
    encis =
        PGPEC.encrypt(
            asList(kp1.getPublicKey(), kp2.getPublicKey()),
            new ByteArrayInputStream("ciao".getBytes()));
    try {
      PGPEC.decrypt(kp3.getPrivateKey(), encis);
      Assertions.fail("fail");
    } catch (final PGPException e) {
    }
  }

  @Test
  public void decodePK() throws Exception {
    final PGPKeyPair kp = PGPEC.encryptionKey();
    final InputStream crypt =
        PGPEC.encrypt(
            Collections.singletonList(
                PGPEC.decodePK(new ByteArrayInputStream(kp.getPublicKey().getEncoded()))),
            new ByteArrayInputStream("ciao".getBytes()));
    assertThat(IOUtils.toByteArray(PGPEC.decrypt(kp.getPrivateKey(), crypt)))
        .isEqualTo("ciao".getBytes());
  }
}
