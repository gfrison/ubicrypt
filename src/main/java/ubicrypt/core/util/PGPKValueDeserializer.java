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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import org.bouncycastle.openpgp.bc.BcPGPPublicKeyRing;

import java.io.IOException;

public class PGPKValueDeserializer extends StdDeserializer<PGPKValue> {
  public PGPKValueDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public PGPKValue deserialize(final JsonParser p, final DeserializationContext ctxt)
    throws IOException {
    return new PGPKValue(new BcPGPPublicKeyRing(p.getBinaryValue()).getPublicKey());
  }
}
