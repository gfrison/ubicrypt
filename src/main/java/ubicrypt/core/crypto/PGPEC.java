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

import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRing;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRing;
import org.bouncycastle.openpgp.examples.PubringDump;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import rx.Observable;
import rx.schedulers.Schedulers;
import ubicrypt.core.Utils;
import ubicrypt.core.util.ConsumerExp;
import ubicrypt.core.util.OnSubscribeInputStream;

import static org.slf4j.LoggerFactory.getLogger;
import static ubicrypt.core.Utils.toStream;

public class PGPEC {
    private static final Logger log = getLogger(PGPEC.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
/*
        try {
            final Field field = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");
            field.setAccessible(true);
            field.set(null, Boolean.FALSE);
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
*/
    }

    public static PGPKeyPair masterKey() {
        return keyPair("ECDSA");
    }

    public static PGPKeyPair encryptionKey() {
        return keyPair("ECDH");
    }

    private static PGPKeyPair keyPair(final String algorithm) {
        PGPKeyPair ecdsaKeyPair = null;
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm, "BC");
            //http://www.ietf.org/rfc/rfc4492.txt
            //5.1.1.  Supported Elliptic Curves Extension
            //https://chrispacia.wordpress.com/2013/10/30/nsa-backdoors-and-bitcoin/
            keyGen.initialize(new ECGenParameterSpec("secp256k1"), new SecureRandom());
            final KeyPair kpSign = keyGen.generateKeyPair();
            ecdsaKeyPair = new JcaPGPKeyPair(alg(algorithm), kpSign, new Date());
        } catch (final Exception e) {
            Throwables.propagate(e);
        }
        return ecdsaKeyPair;
    }

    private static PBESecretKeyEncryptor skEncryptor(final char[] passPhrase, final int algorithm) {
        PGPDigestCalculator sha256Calc = null;
        try {
            sha256Calc = new BcPGPDigestCalculatorProvider().get(algorithm);
        } catch (final PGPException e) {
            Throwables.propagate(e);
        }
        // Note: s2kcount is a number between 0 and 0xff that controls the
        // number of times to iterate the password hash before use. More
        // iterations are useful against offline attacks, as it takes more
        // time to check each password. The actual number of iterations is
        // rather complex, and also depends on the hash function in use.
        // Refer to Section 3.7.1.3 in rfc4880.txt. Bigger numbers give
        // you more iterations.  As a rough rule of thumb, when using
        // SHA256 as the hashing function, 0x10 gives you about 64
        // iterations, 0x20 about 128, 0x30 about 256 and so on till 0xf0,
        // or about 1 million iterations. The maximum you can go to is
        // 0xff, or about 2 million iterations.  I'll use 0xc0 as a
        // default -- about 130,000 iterations.
        return new BcPBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha256Calc, 0xc0).build(passPhrase);
    }


    public static PGPKeyRingGenerator keyRingGenerator() {
        return keyRingGenerator(masterKey(), (PBESecretKeyEncryptor) null);
    }

    public static PGPKeyRingGenerator keyRingGenerator(final PGPKeyPair signPair) {
        return keyRingGenerator(signPair, (PBESecretKeyEncryptor) null);
    }


    private static PGPKeyRingGenerator keyRingGenerator(final PGPKeyPair masterKey, final char[] passPhrase) {
        return keyRingGenerator(masterKey, skEncryptor(passPhrase, HashAlgorithmTags.SHA256));
    }

    private static PGPKeyRingGenerator keyRingGenerator(final PGPKeyPair masterKey, final PBESecretKeyEncryptor encryptor) {
        // Add a self-signature on the id
        final PGPSignatureSubpacketGenerator signhashgen =
                new PGPSignatureSubpacketGenerator();

        // Add signed metadata on the signature.
        // 1) Declare its purpose
        signhashgen.setKeyFlags
                (false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
        // 2) Set preferences for secondary crypto algorithms to use
        //    when sending messages to this key.
        signhashgen.setPreferredSymmetricAlgorithms
                (false, new int[]{
                        SymmetricKeyAlgorithmTags.AES_256,
                        SymmetricKeyAlgorithmTags.AES_192,
                        SymmetricKeyAlgorithmTags.AES_128
                });
        signhashgen.setPreferredHashAlgorithms
                (false, new int[]{
                        HashAlgorithmTags.SHA256,
//                        HashAlgorithmTags.SHA1,
                        HashAlgorithmTags.SHA384,
                        HashAlgorithmTags.SHA512,
                        HashAlgorithmTags.SHA224,
                });
        // 3) Request senders add additional checksums to the
        //    message (useful when verifying unsigned messages.)
        signhashgen.setFeature
                (false, Features.FEATURE_MODIFICATION_DETECTION);

        try {
            return new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION,
                    masterKey,
                    Utils.machineName(),
                    new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
                    signhashgen.generate(),
                    null,
                    new BcPGPContentSignerBuilder(PGPPublicKey.ECDSA, HashAlgorithmTags.SHA256),
                    encryptor);
        } catch (final PGPException e) {
            Throwables.propagate(e);
        }
        return null;
    }

    public static PGPPublicKey signPK(final PGPPublicKey pk, final PGPPrivateKey priv) {
        final PGPSignatureGenerator sGen = new PGPSignatureGenerator(new JcaPGPContentSignerBuilder(PGPPublicKey.ECDSA, PGPUtil.SHA256)
                .setProvider("BC"));

        try {
            sGen.init(PGPSignature.DIRECT_KEY, priv);
        } catch (final PGPException e) {
            Throwables.propagate(e);
        }

        final PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();

        final PGPSignatureSubpacketVector packetVector = spGen.generate();

        sGen.setHashedSubpackets(packetVector);

        try {
            return PGPPublicKey.addCertification(pk, sGen.generateCertification("id", pk));
        } catch (final PGPException e) {
            Throwables.propagate(e);
        }
        return null;
    }


    public static List<PGPPublicKey> readPKring(final InputStream input) {
        final ArrayList<List<PGPPublicKey>> keys = new ArrayList<>();
        try {
            final PGPObjectFactory factory = new PGPObjectFactory(PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator());
            Object obj;
            while ((obj = factory.nextObject()) != null) {
                if (obj instanceof PGPPublicKeyRing) {
                    keys.add(toStream(((PGPPublicKeyRing) obj).getPublicKeys())
                            .filter(PGPPublicKey::isEncryptionKey)
                            .collect(Collectors.toList()));
                }
            }
            return keys.stream().flatMap(List::stream).collect(Collectors.toList());
        } catch (final Exception e) {
            Throwables.propagate(e);
        }
        return null;
    }


    public static PGPSecretKeyRing createSecretKeyRing(final char[] passPhrase) {
        final PGPKeyRingGenerator gen = keyRingGenerator(masterKey(), passPhrase);
        try {
            gen.addSubKey(encryptionKey());
        } catch (final PGPException e) {
            Throwables.propagate(e);
        }
        return gen.generateSecretKeyRing();
    }


    public static PGPSecretKeyRing readSK(final InputStream inputStream) {
        try {
            return new BcPGPSecretKeyRing(inputStream);
        } catch (final Exception e) {
            Throwables.propagate(e);
        }
        return null;
    }

    public static PGPSecretKeyRing readSK(final byte[] bytes) {
        return readSK(new ByteArrayInputStream(bytes));
    }

    public static PGPPrivateKey extractSignKey(final PGPSecretKeyRing skr, final char[] passPhrase) throws PGPException {
        return extractKeyPair(skr, PGPSecretKey::isSigningKey, passPhrase).getPrivateKey();
    }

    public static PGPKeyPair extractEncryptKeyPair(final PGPSecretKeyRing skr, final char[] passPhrase) throws PGPException {
        return extractKeyPair(skr, secc -> !secc.getPublicKey().isMasterKey() && secc.getPublicKey().isEncryptionKey(), passPhrase);
    }

    private static PGPKeyPair extractKeyPair(final PGPSecretKeyRing skr, final Predicate<PGPSecretKey> predicate, final char[] passPhrase) throws PGPException {
        final PGPSecretKey sec = Utils.toStream(skr.getSecretKeys())
                .filter(predicate)
                .findFirst().orElseThrow(() -> new PGPException("key not found"));
        return new PGPKeyPair(sec.getPublicKey(), sec.extractPrivateKey(
                new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(passPhrase)));
    }


    public static InputStream encrypt(final List<PGPPublicKey> pks, final InputStream clearBytes) {
        final PipedInputStream pis = new PipedInputStream();
        final AtomicReference<PipedOutputStream> pos = new AtomicReference<>();
        final AtomicReference<OutputStream> pgpOut = new AtomicReference<>();
        final AtomicReference<OutputStream> lout = new AtomicReference<>();
        try {
            pos.set(new PipedOutputStream(pis));
            final PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

            final PGPEncryptedDataGenerator cPk = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(
                    SymmetricKeyAlgorithmTags.AES_256).setWithIntegrityPacket(true).setProvider("BC").setSecureRandom(
                    new SecureRandom()));

            pks.stream().forEach(pk -> cPk.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(pk).setProvider("BC")));
            pgpOut.set(cPk.open(pos.get(), new byte[1 << 16]));
            lout.set(lData.open(pgpOut.get(), PGPLiteralDataGenerator.BINARY, PGPLiteralData.CONSOLE, new Date(), new byte[1 << 64]));

            Observable.create(new OnSubscribeInputStream(clearBytes, 1 << 64))
                    .subscribeOn(Schedulers.io())
                    .doOnCompleted(() -> Utils.close(lout.get(), pgpOut.get(), pos.get()))
                    .doOnError(err -> {
                        log.error("error on encrypt", err);
                        Utils.close(clearBytes, lout.get(), pgpOut.get(), pos.get());
                    })
                    .subscribe(ConsumerExp.silent(lout.get()::write));
        } catch (final Exception e) {
            Utils.close(clearBytes, lout.get(), pgpOut.get(), pos.get());
            Throwables.propagate(e);
        }
        return pis;
    }

    public static InputStream decrypt(final PGPPrivateKey privateKey, final InputStream cipherText) throws PGPException {
        final JcaPGPObjectFactory pgpF = new JcaPGPObjectFactory(cipherText);

        try {
            final PGPEncryptedDataList encList = (PGPEncryptedDataList) pgpF.nextObject();
            log.trace("decrypt with sk:{}", privateKey.getKeyID());

            final PGPPublicKeyEncryptedData encP = toStream((Iterator<PGPPublicKeyEncryptedData>) encList.iterator())
                    .filter((PGPPublicKeyEncryptedData ed) -> {
                        log.debug("pgp message encrypted with key:{}", ed.getKeyID());
                        return ed.getKeyID() == privateKey.getKeyID();
                    })
                    .findFirst()
                    .orElseThrow(() -> new PGPException("the message is not encrypted with the related public key"));


            try (InputStream clear = encP.getDataStream(new JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(
                    privateKey))) {
                Object next = new JcaPGPObjectFactory(clear).nextObject();
                if (next instanceof PGPCompressedData) {
                    next = new JcaPGPObjectFactory(((PGPCompressedData) next).getDataStream()).nextObject();
                }
                return ((PGPLiteralData) next).getInputStream();
            }
        } catch (final PGPException e) {
            throw e;
        } catch (final Exception e) {
            Throwables.propagate(e);
        }
        return null;
    }


    private static int alg(final String algorithm) {
        switch (algorithm) {
            case "ECDSA":
                return PGPPublicKey.ECDSA;
            case "ECDH":
                return PGPPublicKey.ECDH;
        }
        throw new IllegalArgumentException("algorithm not mapped:" + algorithm);
    }

    public static PGPPublicKey decodePK(final InputStream pk) {
        final PGPObjectFactory pgpFact = new PGPObjectFactory(pk, new JcaKeyFingerprintCalculator());

        try {
            return new BcPGPPublicKeyRing(pk).getPublicKey();
        } catch (final IOException e) {
            Throwables.propagate(e);
        }

        return null;
    }

    public static String algorithm(final int alg) {
        return PubringDump.getAlgorithm(alg);
    }

}
