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
package ubicrypt.core.provider.lock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.InputStream;

import javax.inject.Inject;

import rx.Observable;
import rx.internal.operators.BufferUntilSubscriber;
import rx.subjects.Subject;
import ubicrypt.core.crypto.IPGPService;
import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.ProviderHook;
import ubicrypt.core.provider.ProviderLifeCycle;
import ubicrypt.core.provider.UbiProvider;
import ubicrypt.core.util.InProgressTracker;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {RemoveLockOnShutdownIT.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class RemoveLockOnShutdownIT {
    @Inject
    ProviderLifeCycle providerLifeCycle;
    @Inject
    RemoveLockOnShutdown removeLockOnShutdown;
    @Mock
    UbiProvider provider;
    @Mock
    ProviderHook hook;

    @Before
    public final void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shutdown() throws Exception {
        assertThat(removeLockOnShutdown).isNotNull();
        when(provider.getLockFile()).thenReturn(new RemoteFile() {{
            setRemoteName("remote");
        }});
        when(provider.put(eq("remote"), any(InputStream.class))).thenReturn(Observable.just(true));
        when(provider.delete(eq("remote"))).thenReturn(Observable.just(true));
        when(provider.toString()).thenReturn("test");
        when(hook.getProvider()).thenReturn(provider);
        when(providerLifeCycle.currentlyActiveProviders()).thenReturn(asList(hook));
        removeLockOnShutdown.stop().toBlocking().last();
    }

    @Configuration
    public static class Config {

        @Bean
        public RemoveLockOnShutdown removeLockOnShutdown() {
            return new RemoveLockOnShutdown();
        }

        @Bean
        public ProviderLifeCycle providerLifeCycle() {
            return mock(ProviderLifeCycle.class);
        }


        @Bean
        public Subject appEvents() {
            return BufferUntilSubscriber.create();
        }

        @Bean
        public Subject providerEvent() {
            return BufferUntilSubscriber.create();
        }

        @Bean
        public Subject progressEvents() {
            return BufferUntilSubscriber.create();
        }

        @Bean
        public InProgressTracker inProgressTracker() {
            return new InProgressTracker();
        }

        @Bean
        public static PropertySourcesPlaceholderConfigurer propertyConfigInDev() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public LocalConfig localConfig() {
            return new LocalConfig();
        }

        @Bean
        public int deviceId() {
            return 1;
        }

        @Bean
        IPGPService pgpService() {
            return new IPGPService() {
                @Override
                public InputStream encrypt(InputStream clearBytes) {
                    return clearBytes;
                }

                @Override
                public InputStream decrypt(InputStream cipherText) {
                    return cipherText;
                }

                @Override
                public long keyId() {
                    return 0;
                }
            };
        }

    }

}
