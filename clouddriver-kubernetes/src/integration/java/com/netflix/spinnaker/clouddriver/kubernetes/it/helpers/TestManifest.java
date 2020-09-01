/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.it.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

public class TestManifest {

  private final List<Map<String, Object>> contents;
  private ObjectMapper objectMapper = new ObjectMapper();

  public TestManifest(String path) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    try {
      InputStream is = resourceLoader.getResource(path).getInputStream();
      Yaml yaml = new Yaml();
      Iterable<Object> contentIterable = yaml.loadAll(is);
      contents =
          StreamSupport.stream(contentIterable.spliterator(), false)
              .filter(Objects::nonNull)
              .map(this::coerceManifestToList)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load manifest from path " + path, e);
    }
  }

  public List<Map<String, Object>> getContents() {
    return contents;
  }

  public TestManifest withNamespace(String ns) {
    for (Map<String, Object> manifest : contents) {
      Map<String, Object> metadata = (Map<String, Object>) manifest.get("metadata");
      metadata.put("namespace", ns);
    }
    return this;
  }

  private List<Map<String, Object>> coerceManifestToList(Object manifest) {
    if (manifest instanceof List) {
      return objectMapper.convertValue(manifest, new TypeReference<List<Map<String, Object>>>() {});
    }
    Map<String, Object> singleManifest =
        objectMapper.convertValue(manifest, new TypeReference<Map<String, Object>>() {});
    return Arrays.asList(singleManifest);
  }
}
