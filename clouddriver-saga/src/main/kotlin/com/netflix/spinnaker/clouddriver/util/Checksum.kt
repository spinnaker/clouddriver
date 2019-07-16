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
package com.netflix.spinnaker.clouddriver.util

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.springframework.util.DigestUtils
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Helper object to create MD5 checksums of input data.
 */
object Checksum {
  private val mapper = ObjectMapper()
    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .disable(SerializationFeature.INDENT_OUTPUT)

  fun md5(inputs: Any): String = md5(mapper.convertValue(inputs))

  fun md5(inputs: Map<String, Any?>): String {

    val bytes = try {
      mapper.writeValueAsBytes(inputs)
    } catch (e: JsonProcessingException) {
      throw SystemException("Could not serialize inputs for checksum", e)
    }

    val digest = try {
      MessageDigest.getInstance("MD5")
    } catch (e: NoSuchAlgorithmException) {
      throw SystemException("Could not create MD5 input checksum", e)
    }
    digest.update(bytes)

    return DigestUtils.md5DigestAsHex(digest.digest())
  }
}
