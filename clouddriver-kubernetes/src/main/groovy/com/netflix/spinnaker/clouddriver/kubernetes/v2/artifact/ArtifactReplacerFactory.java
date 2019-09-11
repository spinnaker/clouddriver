/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.Replacer;

public class ArtifactReplacerFactory {
  public static final Replacer DOCKER_IMAGE =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].[?( @.image == \"{%name%}\" )].image")
          .findPath("$..spec.template.spec['containers', 'initContainers'].*.image")
          .nameFromReference(
              ref -> {
                int atIndex = ref.indexOf('@');
                // @ can only show up in image references denoting a digest
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L70
                if (atIndex >= 0) {
                  return ref.substring(0, atIndex);
                }

                // : can be used to denote a port, part of a digest (already matched) or a tag
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L69
                int lastColonIndex = ref.lastIndexOf(':');

                if (lastColonIndex < 0) {
                  return ref;
                }

                // we don't need to check if this is a tag, or a port. ports will be matched lazily
                // if
                // they are numeric, and are treated as tags first:
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L34
                return ref.substring(0, lastColonIndex);
              })
          .type(KubernetesArtifactType.DockerImage)
          .build();
  public static final Replacer CONFIG_MAP_VOLUME =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec.volumes.[?( @.configMap.name == \"{%name%}\" )].configMap.name")
          .findPath("$..spec.template.spec.volumes.*.configMap.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  public static final Replacer SECRET_VOLUME =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec.volumes.[?( @.secret.secretName == \"{%name%}\" )].secret.secretName")
          .findPath("$..spec.template.spec.volumes.*.secret.secretName")
          .type(KubernetesArtifactType.Secret)
          .build();
  public static final Replacer CONFIG_MAP_KEY_VALUE =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?( @.valueFrom.configMapKeyRef.name == \"{%name%}\" )].valueFrom.configMapKeyRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.*.valueFrom.configMapKeyRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  public static final Replacer SECRET_KEY_VALUE =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?( @.valueFrom.secretKeyRef.name == \"{%name%}\" )].valueFrom.secretKeyRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.*.valueFrom.secretKeyRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  public static final Replacer CONFIG_MAP_ENV =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?( @.configMapRef.name == \"{%name%}\" )].configMapRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.*.configMapRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  public static final Replacer SECRET_ENV =
      Replacer.builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?( @.secretRef.name == \"{%name%}\" )].secretRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.*.secretRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  public static final Replacer HPA_DEPLOYMENT =
      Replacer.builder()
          .replacePath(
              "$[?( (@.spec.scaleTargetRef.kind == \"Deployment\" || @.spec.scaleTargetRef.kind == \"deployment\") && @.spec.scaleTargetRef.name == \"{%name%}\" )].spec.scaleTargetRef.name")
          .findPath(
              "$[?( @.spec.scaleTargetRef.kind == \"Deployment\" || @.spec.scaleTargetRef.kind == \"deployment\" )].spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.Deployment)
          .build();
  public static final Replacer HPA_REPLICA_SET =
      Replacer.builder()
          .replacePath(
              "$[?( (@.spec.scaleTargetRef.kind == \"ReplicaSet\" || @.spec.scaleTargetRef.kind == \"replicaSet\") && @.spec.scaleTargetRef.name == \"{%name%}\" )].spec.scaleTargetRef.name")
          .findPath(
              "$[?( @.spec.scaleTargetRef.kind == \"ReplicaSet\" || @.spec.scaleTargetRef.kind == \"replicaSet\" )].spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.ReplicaSet)
          .build();
}
