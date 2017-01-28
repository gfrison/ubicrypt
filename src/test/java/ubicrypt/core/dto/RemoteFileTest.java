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
package ubicrypt.core.dto;

import org.junit.Test;

import java.nio.file.Paths;
import java.util.Random;

import ubicrypt.core.Utils;

import static org.assertj.core.api.Assertions.assertThat;

public class RemoteFileTest {
  @Test
  public void testName() throws Exception {
    final RemoteFile uf = new RemoteFile();
    final Random rnd = new Random();
    final byte[] key = new byte[10];
    rnd.nextBytes(key);
    uf.setPath(Paths.get("tmp"));

    uf.setKey(new Key(key));
    final byte[] marshall = Utils.marshall(uf);
    final RemoteFile uf2 = Utils.unmarshall(marshall, RemoteFile.class);
    assertThat(uf.getKey()).isEqualTo(uf2.getKey());
    assertThat(uf.getId().toString()).isEqualTo(uf2.getId().toString());
  }
}
