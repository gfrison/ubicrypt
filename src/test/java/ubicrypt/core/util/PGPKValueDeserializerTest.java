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
package ubicrypt.core.util;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.junit.Test;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import ubicrypt.core.Utils;
import ubicrypt.core.crypto.PGPEC;

import static org.apache.commons.io.IOUtils.toInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Sets.newLinkedHashSet;

public class PGPKValueDeserializerTest {

  @Test
  public void testName() throws Exception {
    final PGPKeyPair key1 = PGPEC.encryptionKey();

    final BeanTest bean = new BeanTest();
    bean.setPks(newLinkedHashSet(new PGPKValue(key1.getPublicKey())));
    final byte[] bytes = Utils.marshall(bean);
    final BeanTest loaded = Utils.unmarshall(bytes, BeanTest.class);
    final InputStream enc =
        PGPEC.encrypt(
            loaded.getPks().stream().map(PGPKValue::getValue).collect(Collectors.toList()),
            toInputStream("ciao"));
    assertThat(IOUtils.toString(PGPEC.decrypt(key1.getPrivateKey(), enc))).isEqualTo("ciao");
  }

  static class BeanTest {
    Set<PGPKValue> pks;

    public Set<PGPKValue> getPks() {
      return pks;
    }

    public void setPks(final Set<PGPKValue> pks) {
      this.pks = pks;
    }
  }
}
