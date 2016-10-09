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

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import javafx.scene.image.ImageView;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;
import ubicrypt.core.crypto.PGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.LocalRepository;
import ubicrypt.core.provider.ProviderCommander;
import ubicrypt.core.provider.ProviderDescriptor;
import ubicrypt.core.provider.RemoteCtxConf;
import ubicrypt.core.provider.file.FileProvider;
import ubicrypt.core.provider.ftp.FTProvider;
import ubicrypt.core.util.FileInSync;
import ubicrypt.core.util.StreamAppender;
import ubicrypt.core.watch.WatchConf;
import ubicrypt.ui.tree.FileItem;

@Configuration
@Import({WatchConf.class, BaseConf.class, RemoteCtxConf.class})
public class UbiConf {
    private static final Logger log = LoggerFactory.getLogger(UbiConf.class);
    @Autowired
    @Qualifier("keyPair")
    PGPKeyPair keyPair;
    @Inject
    LocalConfig localConfig;


    @PostConstruct
    public void init() {
        log.debug("keyPair:{}", keyPair);
        log.debug("ubiqConfig:{}", localConfig);
    }

    @Bean
    public ShutterDown shutterDown() {
        return new ShutterDown();
    }

    @Bean
    public Path basePath(@Value("${home:@null}") final String home) {
        final Path ret = StringUtils.isEmpty(home) ? Paths.get(System.getProperty("user.home")) : Paths.get(home);
        log.info("home folder:{}", ret);
        return ret;
    }

    @Bean
    public int deviceId(Path basePath) {
        return Utils.deviceId() + basePath.hashCode() % 99;
    }

    @Bean
    public PGPService pgpService() {
        return new PGPService();
    }

    @Bean
    public Subject<Boolean, Boolean> synchProcessing() {
        return PublishSubject.create();
    }


    @Bean
    public FileCommander fileCommander(final Path basePath) {
        return new FileCommander(basePath);
    }


    @Bean
    public InitFileSyncronizer initialSyncronizer() {
        return new InitFileSyncronizer();
    }


    @Bean
    public LocalConfigPersistor initLocalConfPersistor() {
        return new LocalConfigPersistor();
    }


    @Bean
    public LocalRepository localRepository(final Path basePath) {
        return new LocalRepository(basePath);
    }

    /**
     * providers
     */
    @Bean
    public List<ProviderDescriptor> providerDescriptors() {
        return ImmutableList.of(new ProviderDescriptor(FileProvider.class, "file", "local folder", new ImageView("images/folder-48.png")),
                new ProviderDescriptor(FTProvider.class, "ftp", "ftp repository", new ImageView("images/Ftp-48.png")));
    }

    @Bean
    public ProviderCommander providerCommander() {
        return new ProviderCommander();
    }

    @Bean
    public FileFacade fileFacade() {
        return new FileFacade();
    }

    @Bean
    @Scope("prototype")
    public FileItem fileItem(final UbiFile file) {
        return new FileItem(file);
    }

    @Bean
    public FileInSync fileInSync() {
        return new FileInSync();
    }

    @Bean
    public Subject<Object, Object> appEvents() {
        return ReplaySubject.create();
    }

    @Bean
    public Observable<String> logStream() {
        return StreamAppender.getLogStream();
    }

}
