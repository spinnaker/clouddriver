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

package com.netflix.spinnaker.clouddriver.kubernetes.it.utils;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public abstract class KubeTestUtils {

  private static final int SLEEP_STEP_SECONDS = 5;

  public static TestResourceFile loadYaml(String file) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    try {
      InputStream is = resourceLoader.getResource(file).getInputStream();
      Yaml yaml = new Yaml(new SafeConstructor());
      Iterable<Object> contentIterable = yaml.loadAll(is);
      List<Map<String, Object>> content =
          StreamSupport.stream(contentIterable.spliterator(), false)
              .filter(Objects::nonNull)
              .map(KubeTestUtils::coerceManifestToList)
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      return new TestResourceFile(content);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load manifest from file " + file, e);
    }
  }

  private static List<Map<String, Object>> coerceManifestToList(Object manifest) {
    ObjectMapper objectMapper = new ObjectMapper();
    if (manifest instanceof List) {
      return objectMapper.convertValue(manifest, new TypeReference<List<Map<String, Object>>>() {});
    }
    Map<String, Object> singleManifest =
        objectMapper.convertValue(manifest, new TypeReference<Map<String, Object>>() {});
    return Arrays.asList(singleManifest);
  }

  public static TestResourceFile loadJson(String file) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    try {
      InputStream is = resourceLoader.getResource(file).getInputStream();
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(is);
      List<Map<String, Object>> content;
      if (jsonNode.isArray()) {
        content =
            objectMapper.convertValue(jsonNode, new TypeReference<List<Map<String, Object>>>() {});
      } else {
        content =
            Arrays.asList(
                objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {}));
      }
      return new TestResourceFile(content);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load manifest from file " + file, e);
    }
  }

  public static void repeatUntilTrue(
      BooleanSupplier func, long duration, TimeUnit unit, String errorMsg)
      throws InterruptedException {
    long durationSeconds = unit.toSeconds(duration);
    for (int i = 0; i < (durationSeconds / SLEEP_STEP_SECONDS); i++) {
      if (!func.getAsBoolean()) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(SLEEP_STEP_SECONDS));
      } else {
        return;
      }
    }
    fail(errorMsg);
  }

  public static class TestResourceFile {

    private List<Map<String, Object>> content;

    public TestResourceFile(List<Map<String, Object>> content) {
      this.content = content;
    }

    public List<Map<String, Object>> asList() {
      return content;
    }

    public Map<String, Object> asMap() {
      return content.get(0);
    }

    public TestResourceFile withValue(String path, Object value) {
      String[] parts = path.split("\\.");

      for (Map<String, Object> entry : content) {
        for (int i = 0; i < parts.length; i++) {
          if (i == parts.length - 1) {
            entry.put(parts[i], value);
            break;
          }
          if (parts[i].matches("^.*\\[[0-9]*]$")) {
            String key = parts[i].substring(0, parts[i].indexOf('['));
            int index =
                Integer.parseInt(
                    parts[i].substring(parts[i].indexOf('[') + 1, parts[i].indexOf(']')));
            List<Map<String, Object>> list = (List<Map<String, Object>>) entry.get(key);
            entry = list.get(index);
          } else if (!entry.containsKey(parts[i])) {
            entry.put(parts[i], new HashMap<>());
            entry = (Map<String, Object>) entry.get(parts[i]);
          } else {
            entry = (Map<String, Object>) entry.get(parts[i]);
          }
        }
      }

      return this;
    }
  }
}
