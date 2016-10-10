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
package ubicrypt.core;

import com.google.common.base.Throwables;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import ubicrypt.core.crypto.PGPEC;
import ubicrypt.core.dto.LocalConfig;

import static ubicrypt.core.Utils.configFile;
import static ubicrypt.core.Utils.marshall;
import static ubicrypt.core.Utils.marshallIs;
import static ubicrypt.core.Utils.securityFile;
import static ubicrypt.core.Utils.write;
import static ubicrypt.core.crypto.PGPEC.encrypt;

public class FixPassPhraseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(FixPassPhraseInitializer.class);
    private char[] password;

    public FixPassPhraseInitializer(final char[] password) {
        this.password = password;
    }

    private Tuple2<PGPPrivateKey, PGPKeyPair> readOrCreate() {
        try {
            final Path secFile = securityFile();
            final PGPKeyPair keyPair;
            final PGPSecretKeyRing kring;
            final PGPPrivateKey signkey;
            if (secFile.toFile().exists()) {
                kring = PGPEC.readSK(Files.newInputStream(secFile));
                keyPair = PGPEC.extractEncryptKeyPair(kring, password);
                signkey = PGPEC.extractSignKey(kring, password);
            } else {
                kring = PGPEC.createSecretKeyRing(password);
                keyPair = PGPEC.extractEncryptKeyPair(kring, password);
                signkey = PGPEC.extractSignKey(kring, password);
                write(secFile, new ByteArrayInputStream(kring.getEncoded())).doOnError(err -> log.error(err.getMessage(), err))
                        .subscribe();
            }
            return Tuple.of(signkey, keyPair);
        } catch (final Exception e) {

            Throwables.propagate(e);
        }
        return null;
    }

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
        final Tuple2<PGPPrivateKey, PGPKeyPair> tuple = readOrCreate();
        final PGPKeyPair keyPair = tuple.getT2();
        password = null;
        log.debug("pub key id:{}", keyPair.getPublicKey().getKeyID());
        applicationContext.getBeanFactory().registerSingleton("keyPair", keyPair);
        applicationContext.getBeanFactory().registerSingleton("signKey", tuple.getT1());
        final boolean encrypt = Boolean.valueOf(applicationContext.getEnvironment().getProperty("pgp.enabled", "true"));

        final Path configFile = configFile();
        final LocalConfig config;
        if (!configFile.toFile().exists()) {
            config = new LocalConfig();
            write(configFile,
                    encrypt ? encrypt(Collections.singletonList(keyPair.getPublicKey()), marshallIs(config)) :
                            new ByteArrayInputStream(marshall(config))).subscribe();
        } else {
            InputStream configIs = null;
            try {
                configIs = encrypt ? PGPEC.decrypt(keyPair.getPrivateKey(), Utils.readIs(configFile)) : Utils.readIs(configFile);
            } catch (final PGPException e) {
                Throwables.propagate(e);
            }
            config = Utils.unmarshall(configIs, LocalConfig.class);
        }
        applicationContext.getBeanFactory().registerSingleton("ubiqConfig", config);
    }

    protected char[] getPassPhrase() {
        return "ciao".toCharArray();
    }

    protected char[] getPassPhraseNew() {
        return "ciao".toCharArray();
    }
}
