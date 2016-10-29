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
package ubicrypt.core.remote;

import org.junit.Before;
import org.junit.Test;

import ubicrypt.core.FileProvenience;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.provider.UbiProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class OnErrorRemoteTest {

    private OnErrorRemote test;

    @Before
    public void setUp() throws Exception {
        test = new OnErrorRemote(mock(UbiProvider.class), mock(RemoteRepository.class));
    }

    @Test
    public void test1() throws Exception {
        UbiFile file = new LocalFile();
        FileProvenience fp = new FileProvenience(file, mock(RemoteRepository.class));
        RemoteConfig rconfig = new RemoteConfig();
        final RemoteFile remoteFile = RemoteFile.createFrom(file);
        remoteFile.setError(true);
        rconfig.getRemoteFiles().add(remoteFile);
        assertThat(test.test(fp, rconfig)).isTrue();
    }
}
