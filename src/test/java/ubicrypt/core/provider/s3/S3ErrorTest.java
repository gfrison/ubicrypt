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
package ubicrypt.core.provider.s3;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ErrorTest {

  @Test
  public void name() throws Exception {
    S3Error error =
        S3Error.from(
            "<Error><Code>BucketAlreadyOwnedByYou</Code><Message>Your previous request to create the named bucket succeeded and you already own it.</Message><BucketName>qwerqwer</BucketName><RequestId>0DEA2AC21B62B766</RequestId><HostId>rVOvXyMNgX7dWsIOGCq8C12wdb9bAEhjGnLxzHaDHDa0l4BxkPc2E4WDQ0Hz0ZE6fTSwbM05Pro=</HostId></Error>");
    assertThat(error.getCode()).isEqualTo("BucketAlreadyOwnedByYou");
  }
}
