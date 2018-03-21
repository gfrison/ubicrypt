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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import ubicrypt.core.Utils;
import ubicrypt.core.exp.NotFoundException;
import ubicrypt.core.provider.ProviderStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class S3ProviderTest {

  private String bucket = UUID.randomUUID().toString();

  @After
  public void tearDown() throws Exception {
    S3Conf conf = createConf();
    AmazonS3ClientBuilder clientb =
        AmazonS3ClientBuilder.standard()
            .withCredentials(
                new AWSCredentialsProvider() {
                  @Override
                  public AWSCredentials getCredentials() {
                    return new AWSCredentials() {
                      @Override
                      public String getAWSAccessKeyId() {
                        return conf.getAccessKeyId();
                      }

                      @Override
                      public String getAWSSecretKey() {
                        return conf.getSecrectKey();
                      }
                    };
                  }

                  @Override
                  public void refresh() {}
                });
    if (conf.getRegion() != null) {
      clientb.withRegion(conf.getRegion());
    }
    //                ClientConfiguration clientConfiguration = new ClientConfiguration();
    //                clientConfiguration.setSignerOverride("S3SignerType");
    //                clientb.setClientConfiguration(clientConfiguration);
    AmazonS3 client = clientb.build();
    client.deleteBucket(conf.getBucket());
  }

  @Test
  public void name() throws Exception {
    if (StringUtils.isEmpty(System.getenv("aws.accessKey"))) {
      return;
    }
    S3Conf conf = createConf();
    AmazonS3Client client =
        new AmazonS3Client(
            new AWSCredentials() {
              @Override
              public String getAWSAccessKeyId() {
                return conf.getAccessKeyId();
              }

              @Override
              public String getAWSSecretKey() {
                return conf.getSecrectKey();
              }
            });
    client.setRegion(Region.getRegion(conf.getRegion()));
    S3Provider test = new S3Provider();
    test.setConf(conf);
    assertThat(test.init(Utils.deviceId()).toBlocking().first())
        .isEqualTo(ProviderStatus.initialized);
    String id = test.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().first();
    assertThat(IOUtils.toString(test.get(id).toBlocking().first(), Charset.defaultCharset()))
        .isEqualTo("ciao");
    assertThat(test.put(id, new ByteArrayInputStream("ciao2".getBytes())).toBlocking().first())
        .isTrue();
    assertThat(IOUtils.toString(test.get(id).toBlocking().first(), Charset.defaultCharset()))
        .isEqualTo("ciao2");
    assertThat(test.delete(id).toBlocking().first()).isTrue();
    try {
      assertThat(IOUtils.toString(test.get(id).toBlocking().first(), Charset.defaultCharset()))
          .isEqualTo("ciao2");
      Assertions.fail("the pid should be deleted");
    } catch (NotFoundException e) {

    }
  }

  @Test
  public void contentLength() throws Exception {
    if (StringUtils.isEmpty(System.getenv("aws.accessKey"))) {
      return;
    }
    S3Provider test = new S3Provider();
    test.setConf(createConf());
    test.init(Utils.deviceId()).toBlocking().first();

    String id = test.post(new ByteArrayInputStream("ciao".getBytes())).toBlocking().first();
    assertThat(IOUtils.toString(test.get(id).toBlocking().first(), Charset.defaultCharset()))
        .isEqualTo("ciao");
    assertThat(test.put(id, new ByteArrayInputStream("ciao2".getBytes())).toBlocking().first())
        .isTrue();
    assertThat(IOUtils.toString(test.get(id).toBlocking().first(), Charset.defaultCharset()))
        .isEqualTo("ciao2");
    assertThat(test.delete(id).toBlocking().first()).isTrue();
  }

  private S3Conf createConf() {
    S3Conf conf = new S3Conf();
    conf.setAccessKeyId(System.getenv("aws.accessKey"));
    conf.setSecrectKey(System.getenv("aws.secret"));
    conf.setRegion(Regions.EU_CENTRAL_1);
    conf.setBucket(bucket);
    return conf;
  }

  @Test(expected = NotFoundException.class)
  public void notfound() throws Exception {
    if (StringUtils.isEmpty(System.getenv("aws.accessKey"))) {
      throw new NotFoundException("");
    }
    S3Provider test = new S3Provider();
    test.setConf(createConf());
    test.init(Utils.deviceId()).toBlocking().first();
    test.get(bucket + ":sdfsefwf23").toBlocking().first();
  }
}
