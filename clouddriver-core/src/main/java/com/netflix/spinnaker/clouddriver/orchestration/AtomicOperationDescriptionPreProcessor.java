/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import java.io.IOException;
import java.util.Map;

/**
 * Provides an extension point for manipulating an {@code AtomicOperation} context prior to
 * execution.
 */
public interface AtomicOperationDescriptionPreProcessor {
  boolean supports(Class descriptionClass);

  Map<String, Object> process(Map<String, Object> description);

  default <T> T mapTo(ObjectMapper objectMapper, Map<String, Object> description, Class<T> clazz)
      throws IOException {
    ObjectNode objectNode = objectMapper.valueToTree(description);
    return objectMapper.readValue(new TreeTraversingParser(objectNode, objectMapper), clazz);
  }
}
