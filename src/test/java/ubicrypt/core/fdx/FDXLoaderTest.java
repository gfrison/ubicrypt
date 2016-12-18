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
package ubicrypt.core.fdx;

import com.google.api.client.util.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import reactor.fn.tuple.Tuple2;
import ubicrypt.core.dto.FileIndex;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.util.IObjectSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static rx.Observable.create;
import static rx.Observable.just;

@RunWith(MockitoJUnitRunner.class)
public class FDXLoaderTest {
  @Mock IObjectSerializer serializer;

  @Test
  public void noNext() throws Exception {
    RemoteFile indexFile = new RemoteFile();
    when(serializer.getObject(eq(indexFile), any(Class.class))).thenReturn(just(new FileIndex()));
    FDXLoader loader = new FDXLoader(serializer, indexFile);
    Iterator<Tuple2<RemoteFile, List<RemoteFile>>> it = create(loader).toBlocking().getIterator();
    assertThat(it.hasNext()).isTrue();
    Tuple2<RemoteFile, List<RemoteFile>> tupla = it.next();
    assertThat(tupla.getT1()).isEqualTo(indexFile);
    assertThat(tupla.getT2()).isNull();
    assertThat(it.hasNext()).isFalse();
  }

  @Test
  public void multiNext() throws Exception {
    AtomicInteger ai = new AtomicInteger();
    FileIndex idx = Mockito.mock(FileIndex.class);
    when(idx.getNextIndex())
        .thenAnswer(
            new Answer<RemoteFile>() {
              @Override
              public RemoteFile answer(InvocationOnMock invocation) throws Throwable {
                if (ai.incrementAndGet() < 10) {
                  return new RemoteFile();
                }
                return null;
              }
            });
    when(idx.getFiles())
        .thenAnswer(
            new Answer<List<RemoteFile>>() {
              @Override
              public List<RemoteFile> answer(InvocationOnMock invocation) throws Throwable {
                return IntStream.range(0, ai.get())
                    .mapToObj(i -> new RemoteFile())
                    .collect(Collectors.toList());
              }
            });
    when(serializer.getObject(any(RemoteFile.class), any(Class.class))).thenReturn(just(idx));
    Iterator<Tuple2<RemoteFile, List<RemoteFile>>> it =
        create(new FDXLoader(serializer, new RemoteFile())).toBlocking().getIterator();
    ArrayList<Tuple2<RemoteFile, List<RemoteFile>>> list = Lists.newArrayList(it);
    assertThat(list).hasSize(10);
    final int[] i = {0};
    list.forEach(
        tupla -> {
          assertThat(tupla.getT2()).hasSize(i[0]++);
        });
  }
}
