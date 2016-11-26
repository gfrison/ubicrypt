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
package ubicrypt;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Manifest;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import rx.subjects.Subject;
import ubicrypt.core.FixPassPhraseInitializer;
import ubicrypt.core.UbiConf;
import ubicrypt.core.Utils;
import ubicrypt.core.events.ShutdownOK;
import ubicrypt.core.events.ShutdownRequest;
import ubicrypt.ui.ControllerFactory;
import ubicrypt.ui.StackNavigator;

import static java.util.concurrent.TimeUnit.MINUTES;
import static ubicrypt.core.Utils.securityFile;
import static ubicrypt.core.Utils.ubiqFolder;
import static ubicrypt.core.crypto.PGPEC.encrypt;
import static ubicrypt.core.crypto.PGPEC.encryptionKey;
import static ubicrypt.ui.Anchor.anchor;

@Lazy
public class UbiCrypt extends Application {
    private static final Logger log = LoggerFactory.getLogger(UbiCrypt.class);
    public static String[] arguments = {};
    private ConfigurableApplicationContext ctx;
    private final static AtomicBoolean stopped = new AtomicBoolean(false);
    private Runnable shutdown = () -> {
        if (stopped.compareAndSet(false, true)) {
            if (ctx != null) {
                Platform.runLater(() -> anchor().browse("wait", "Shutting down UbiCrypt..."));

                Subject appEvents = ctx.getBeanFactory().getBean("appEvents", Subject.class);
                log.info("shutdown request, waiting for all components acks...");
                CountDownLatch cd = new CountDownLatch(1);
                appEvents.filter(event -> event instanceof ShutdownOK)
                        .subscribe(next -> cd.countDown());
                appEvents.onNext(new ShutdownRequest());
                try {
                    if (cd.await(1, MINUTES)) {
                        log.info("shutting gracefully down");
                    } else {
                        log.info("shutting process timed out");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ctx.close();
            }
            Platform.exit();
        }
    };


    public static void main(final String[] args) throws IOException, InterruptedException {
        arguments = args;
        ubiqFolder().toFile().mkdirs();
        if (Utils.isAppInUse(ubiqFolder())) {
            log.error("UbiCrypt already running. Quit");
            return;
        }
        Application.launch(args);
    }

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void stop() throws Exception {
        shutdown.run();
        super.stop();
    }

    @Override
    public void start(final Stage stage) throws Exception {
        setUserAgentStylesheet(STYLESHEET_MODENA);
        stage.setTitle("UbiCrypt");
        anchor().setStage(stage);
        try {
            final PGPKeyPair kp = encryptionKey();
            encrypt(Collections.singletonList(kp.getPublicKey()), new ByteArrayInputStream(StringUtils.repeat("ciao", 1).getBytes()));
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Strong Encryption Unsupported");
            alert.setHeaderText("JCE Unlimited Strength Jurisdiction policy files required");
            alert.setContentText("You can install the Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files, which are required to use strong encryption.\n" +
                    "Download the files and instructions for Java 8.\n" +
                    "Locate the jre\\lib\\security directory for the Java instance that the UbiCrypt is using.\n" +
                    "For example, this location might be: C:\\Program Files\\Java\\jre8\\lib\\security.\n" +
                    "Replace these two files with the .jar files included in the JCE Unlimited Strength Jurisdiction Policy Files download.\n" +
                    "Stop and restart the UbiCrypt.\n\n\n\n\n\n");
            ButtonType icePage = new ButtonType("Go to JCE download Page");
            alert.getButtonTypes().addAll(icePage);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == icePage) {
                getHostServices().showDocument("http://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html");
            }
            Platform.exit();
        }
        final File file = securityFile().toFile();
        stage.setScene(anchor().showScene(file.exists() ? "login" : "createKey"));
/*
        stage.setWidth(500);
        stage.setHeight(500);
*/
        stage.show();
        final UbiCrypt ubiCrypt = this;
        anchor().getPasswordStream().subscribe(pwd -> {
            final SpringApplication app = new SpringApplication(UbiConf.class);
            app.setRegisterShutdownHook(false);
            app.addInitializers(new FixPassPhraseInitializer(pwd));
            app.setLogStartupInfo(true);
            ctx = app.run(arguments);

            ctx.getAutowireCapableBeanFactory().autowireBean(ubiCrypt);
            ctx.getBeanFactory().registerSingleton("stage", stage);
            ctx.getBeanFactory().registerSingleton("ctx", anchor());
            ControllerFactory cfactory = new ControllerFactory(ctx);
            StackNavigator navigator = new StackNavigator(null, "main", cfactory);
            stage.setScene(new Scene(navigator.open()));
        });

        stage.setOnCloseRequest(windowEvent -> shutdown.run());
        Runtime.getRuntime().addShutdownHook(new Thread(shutdown));


    }

    public static String getVersion() {
        String version = null;
        // try to load from maven properties first
        try {
            try (final InputStream is = UbiCrypt.class.getResourceAsStream("META-INF/MANIFEST.MF")) {
                if (is != null) {
                    Manifest manifest = new Manifest(is);
                    version = manifest.getMainAttributes().getValue("Implementation-Version");
                }
            }
        } catch (final Exception e) {
            // ignore
        }
        // fallback to using Java API
        if (version == null) {
            final Package aPackage = UbiCrypt.class.getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }
        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }
        return version;
    }
}
