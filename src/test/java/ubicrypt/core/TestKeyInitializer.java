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

public class TestKeyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
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
