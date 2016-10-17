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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import ubicrypt.core.dto.LocalConfig;
import ubicrypt.core.dto.LocalFile;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.provider.IRepository;
import ubicrypt.core.provider.LocalRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class FileSynchronizerTest {

    private final static int deviceId = Utils.deviceId();

    @Ignore
    @Test
    public void packFiles() throws Exception {
        final FileSynchronizer ifs = new FileSynchronizer();
        final UUID uuid = UUID.randomUUID();
        ifs.localConfig = new LocalConfig() {{
            getLocalFiles().add(new LocalFile() {{
                setId(uuid);
                getVclock().increment(deviceId);
                getVclock().increment(deviceId);
            }});
        }};
        ifs.localRepository = new LocalRepository(TestUtils.tmp);

        final Multimap<UUID, FileProvenience> map = ifs.packFilesById().toBlocking().last();
        assertThat(map.keySet().size()).isEqualTo(1);
        assertThat(map.get(uuid)).hasSize(2);
        final List<FileProvenience> collect = map.get(uuid).stream().filter(fp -> fp.getOrigin().isLocal()).collect(Collectors.toList());
        assertThat(collect).hasSize(1);
        assertThat(map.get(uuid).stream().filter(fp -> !fp.getOrigin().isLocal()).collect(Collectors.toList())).hasSize(1);

    }

    @Test
    public void conflicts() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock(IRepository.class));

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid, fp2);
        final Multimap<UUID, FileProvenience> conflicts = FileSynchronizer.conflicts(input);
        assertThat(conflicts.keySet().size()).isEqualTo(1);
        assertThat(conflicts.get(uuid)).hasSize(2);

        assertThat(FileSynchronizer.withoutConflicts(input).keySet()).hasSize(0);
    }

    @Test
    public void conflictsDifferents() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final UUID uuid1 = UUID.randomUUID();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock(IRepository.class));

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid1);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid1, fp2);
        final Multimap<UUID, FileProvenience> conflicts = FileSynchronizer.conflicts(input);
        assertThat(conflicts.keySet().size()).isEqualTo(0);
        assertThat(FileSynchronizer.withoutConflicts(input).keySet()).hasSize(2);
    }

    @Test
    public void conflictsMix() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final UUID uuid1 = UUID.randomUUID();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock(IRepository.class));
        final FileProvenience fp3 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid1);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid, fp3, uuid1, fp2);
        final Multimap<UUID, FileProvenience> conflicts = FileSynchronizer.conflicts(input);
        assertThat(conflicts.keySet().size()).isEqualTo(1);
        assertThat(FileSynchronizer.withoutConflicts(input).keySet()).hasSize(1);
    }

    @Test
    public void noconflicts() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock(IRepository.class));

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid);
            getVclock().increment(1);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid, fp2);
        final Multimap<UUID, FileProvenience> conflicts = FileSynchronizer.conflicts(input);
        assertThat(conflicts.keySet().size()).isEqualTo(0);

        assertThat(FileSynchronizer.withoutConflicts(input).keySet()).hasSize(1);

    }

    @Test
    public void noconflictsOnlyLocal() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final UUID uuid1 = UUID.randomUUID();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock(IRepository.class));

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid1);
            getVclock().increment(1);
            getVclock().increment(2);
        }}, mock(IRepository.class));

        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid1, fp2);
        final Multimap<UUID, FileProvenience> conflicts = FileSynchronizer.conflicts(input);
        assertThat(conflicts.keySet().size()).isEqualTo(0);

        assertThat(FileSynchronizer.withoutConflicts(input).keySet()).hasSize(2);

    }

    @Test
    public void max() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final IRepository mock1 = mock(IRepository.class);
        doReturn(true).when(mock1).isLocal();
        final IRepository mock2 = mock(IRepository.class);
        doReturn(false).when(mock2).isLocal();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock1);

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid);
            getVclock().increment(1);
            getVclock().increment(2);
        }}, mock2);
        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid, fp2);
        final HashMap<UUID, FileProvenience> max = FileSynchronizer.max(input);
        assertThat(max).hasSize(1);
        assertThat(max.get(uuid).getOrigin().isLocal()).isFalse();

    }

    @Test
    public void max2() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final IRepository mock1 = mock(IRepository.class);
        doReturn(true).when(mock1).isLocal();
        final IRepository mock2 = mock(IRepository.class);
        doReturn(false).when(mock2).isLocal();
        final FileProvenience fp1 = new FileProvenience(new LocalFile() {{
            setId(uuid);
            getVclock().increment(1);
        }}, mock1);

        final FileProvenience fp2 = new FileProvenience(new RemoteFile() {{
            setId(uuid);
            getVclock().increment(1);
            getVclock().increment(1);
        }}, mock2);
        final Multimap<UUID, FileProvenience> input = ImmutableMultimap.of(uuid, fp1, uuid, fp2);
        final HashMap<UUID, FileProvenience> max = FileSynchronizer.max(input);
        assertThat(max).hasSize(1);
        assertThat(max.get(uuid).getOrigin().isLocal()).isFalse();

    }

    @Test
    public void updateLocal() throws Exception {


    }
}
