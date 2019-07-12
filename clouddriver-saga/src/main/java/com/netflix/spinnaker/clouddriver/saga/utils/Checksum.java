/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.saga.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.util.DigestUtils;

public class Checksum {
  private static final ObjectMapper CHECKSUM_MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(SerializationFeature.INDENT_OUTPUT);
  private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
      new TypeReference<Map<String, Object>>() {};

  public static String md5(@Nonnull Object inputs) {
    return md5(CHECKSUM_MAPPER.convertValue(inputs, MAP_TYPE_REFERENCE));
  }

  public static String md5(@Nonnull Map<String, Object> inputs) {
    byte[] bytes;
    try {
      bytes = CHECKSUM_MAPPER.writeValueAsBytes(inputs);
    } catch (JsonProcessingException e) {
      throw new SystemException("Could not serialize inputs for checksum", e);
    }

    MessageDigest m;
    try {
      m = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new SystemException("Could not create SHA1 input checksum", e);
    }
    m.update(bytes);

    return DigestUtils.md5DigestAsHex(m.digest());
  }
}
