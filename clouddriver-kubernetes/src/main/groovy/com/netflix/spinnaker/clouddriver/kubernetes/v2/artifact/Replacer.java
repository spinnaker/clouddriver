/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

@Builder
@ParametersAreNonnullByDefault
@Slf4j
public class Replacer {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Nonnull private final String replacePath;
  @Nonnull private final String findPath;
  @Nullable private final Function<String, String> nameFromReference;
  @Nonnull private final KubernetesArtifactType type;

  private static String substituteField(String result, String fieldName, @Nullable String field) {
    return result.replace("{%" + fieldName + "%}", Optional.ofNullable(field).orElse(""));
  }

  private static String processPath(String path, Artifact artifact) {
    String result = substituteField(path, "name", artifact.getName());
    result = substituteField(result, "type", artifact.getType());
    result = substituteField(result, "version", artifact.getVersion());
    result = substituteField(result, "reference", artifact.getReference());
    return result;
  }

  private ArrayNode findAll(DocumentContext obj) {
    return obj.read(findPath);
  }

  @Nonnull
  private Artifact artifactFromReference(String s) {
    return Artifact.builder().type(type.getType()).reference(s).name(nameFromReference(s)).build();
  }

  @Nonnull
  private String nameFromReference(String s) {
    if (nameFromReference != null) {
      return nameFromReference.apply(s);
    } else {
      return s;
    }
  }

  @Nonnull
  ImmutableCollection<Artifact> getArtifacts(DocumentContext document) {
    return mapper
        .<List<String>>convertValue(findAll(document), new TypeReference<List<String>>() {})
        .stream()
        .map(this::artifactFromReference)
        .collect(toImmutableList());
  }

  @Nonnull
  ImmutableCollection<Artifact> replaceArtifacts(
      DocumentContext obj, Collection<Artifact> artifacts) {
    ImmutableSet.Builder<Artifact> replacedArtifacts = new ImmutableSet.Builder<>();
    artifacts.forEach(
        artifact -> {
          boolean wasReplaced = replaceIfPossible(obj, artifact);
          if (wasReplaced) {
            replacedArtifacts.add(artifact);
          }
        });
    return replacedArtifacts.build();
  }

  private boolean replaceIfPossible(DocumentContext obj, @Nullable Artifact artifact) {
    if (artifact == null || StringUtils.isEmpty(artifact.getType())) {
      throw new IllegalArgumentException("Artifact and artifact type must be set.");
    }

    if (!artifact.getType().equals(type.getType())) {
      return false;
    }

    String jsonPath = processPath(replacePath, artifact);

    log.debug("Processed jsonPath == {}", jsonPath);

    Object get;
    try {
      get = obj.read(jsonPath);
    } catch (PathNotFoundException e) {
      return false;
    }
    if (get == null || (get instanceof ArrayNode && ((ArrayNode) get).size() == 0)) {
      return false;
    }

    log.info("Found valid swap for " + artifact + " using " + jsonPath + ": " + get);
    obj.set(jsonPath, artifact.getReference());

    return true;
  }
}
