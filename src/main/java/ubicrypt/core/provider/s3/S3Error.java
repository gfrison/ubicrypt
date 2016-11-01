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
package ubicrypt.core.provider.s3;

import com.thoughtworks.xstream.XStream;

public class S3Error {
    private final static XStream xstream = new XStream();

    static {
        xstream.alias("Error", S3Error.class);
        xstream.aliasField("Code", S3Error.class, "code");
        xstream.aliasField("Message", S3Error.class, "message");
        xstream.aliasField("BucketName", S3Error.class, "bucket");
        xstream.ignoreUnknownElements();
    }

    private String code;
    private String message;
    private String bucket;

    public static S3Error from(final String xml) {
        return (S3Error) xstream.fromXML(xml);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
