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
package ubicrypt.core.crypto;

import com.google.common.base.Throwables;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.spec.SecretKeySpec;

import static com.google.common.base.Preconditions.checkNotNull;

public class AESGCM {
    private final static int keyLenght = 32;//256 bits
    private static final AtomicLong ivl = new AtomicLong(System.currentTimeMillis());

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] rndKey() {

        //TODO:VMPCRandomGenerator
        final SecureRandom rnd = new SecureRandom();
        rnd.setSeed(System.currentTimeMillis());
        final byte[] key = new byte[keyLenght];
        rnd.nextBytes(key);
        return key;
    }


    public static InputStream encryptIs(final byte[] key, final InputStream plain) {
        checkNotNull(key, "key must not be null");
        checkNotNull(plain, "plain must not be null");
        final byte[] iv = ByteBuffer.allocate(keyLenght).putLong(ivl.getAndIncrement()).array();
        final AEADBlockCipher cipher = cipherObject(true, new SecretKeySpec(key, "AES"), iv);
        return new SequenceInputStream(new ByteArrayInputStream(iv), new CipherInputStream(plain, cipher));
    }

    public static InputStream decryptIs(final byte[] key, final InputStream cipherStream) {
        checkNotNull(key, "key must not be null");
        checkNotNull(cipherStream, "cipherStream must not be null");
        final byte[] iv = new byte[keyLenght];
        try {
            cipherStream.read(iv);
            final AEADBlockCipher cipher = cipherObject(false, new SecretKeySpec(key, "AES"), iv);
            return new CipherInputStream(cipherStream, cipher);
        } catch (final Exception e) {
            Throwables.propagate(e);
        }
        return null;
    }

    private static AEADBlockCipher cipherObject(final boolean encrypt, final SecretKeySpec key, final byte[] iv) {
        final AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
        cipher.init(encrypt, new AEADParameters(new KeyParameter(key.getEncoded()), 128, iv));
        return cipher;
    }
}
