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
package ubicrypt.core.crypto;

import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import static ubicrypt.core.crypto.AESGCM.decryptIs;
import static ubicrypt.core.crypto.AESGCM.encryptIs;

public class AESGCMTest {

  @Test
  public void testName() throws Exception {
    final byte[] key = AESGCM.rndKey();
    final ByteArrayInputStream bis = new ByteArrayInputStream("Ciao".getBytes());
    final InputStream cipherStream = new DeflaterInputStream(encryptIs(key, bis));
    final InputStream decrypt2InputStream = decryptIs(key, new InflaterInputStream(cipherStream));
    Assertions.assertThat(IOUtils.toString(decrypt2InputStream)).isEqualTo("Ciao");
  }
}
