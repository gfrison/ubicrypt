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
package ubicrypt.core.provider.gdrive;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;

import rx.functions.Func1;
import ubicrypt.core.TestUtils;
import ubicrypt.core.Utils;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@ConditionalOnProperty(name = "user.name", value = "gfrison")
public class GDriveProviderTest {
  private static final Logger log = getLogger(GDriveProviderTest.class);
  private static GDriveConf conf;
  final Func1<InputStream, String> stringFunc1 =
      is -> {
        try {
          return IOUtils.toString(is, Charset.defaultCharset());
        } catch (IOException e) {
          e.printStackTrace();
        }
        return null;
      };
  private GDriveProvider gdrive;

  @BeforeClass
  public static void init() throws GeneralSecurityException, IOException {
    conf = TestUtils.createGDriveConf();
  }

  @Before
  public void initialize() throws Exception {
    gdrive = new GDriveProvider(conf);
    assertThat(gdrive.init(Utils.deviceId()).toBlocking().first())
        .isEqualTo(ProviderStatus.initialized);
  }

  @Test
  public void post() throws Exception {
    String id = null;
    try {
      id = gdrive.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().last();
      assertThat(id).isNotNull();
      assertThat(gdrive.get(id).map(stringFunc1).toBlocking().last()).isEqualTo("ciao");
    } finally {
      if (id != null) {
        assertThat(gdrive.delete(id).toBlocking().last()).isTrue();
      }
    }
  }

  @Test
  public void update() throws Exception {
    String id = null;
    try {
      id = gdrive.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().last();
      assertThat(id).isNotNull();
      assertThat(gdrive.put(id, new ByteArrayInputStream("ciao2".getBytes())).toBlocking().last())
          .isTrue();
      assertThat(gdrive.get(id).map(stringFunc1).toBlocking().last()).isEqualTo("ciao2");
    } finally {
      if (id != null) {
        assertThat(gdrive.delete(id).toBlocking().last()).isTrue();
      }
    }
  }

  @Test(expected = NotFoundException.class)
  public void notFound() throws Exception {
    gdrive.get("notexists").map(stringFunc1).toBlocking().last();
  }
}
