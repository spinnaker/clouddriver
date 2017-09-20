/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.view.model;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class KubernetesServerGroup extends ManifestBasedModel implements ServerGroup {
  Boolean disabled;
  Long createdTime;
  Set<String> zones = new HashSet<>();
  Set<Instance> instances = new HashSet<>();
  Set<String> loadBalancers = new HashSet<>();
  Set<String> securityGroups = new HashSet<>();
  Map<String, Object> launchConfig = new HashMap<>();
  InstanceCounts instanceCounts;
  Capacity capacity = new Capacity();
  ImageSummary imageSummary;
  ImagesSummary imagesSummary;
  KubernetesManifest manifest;

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  public KubernetesServerGroup(KubernetesManifest manifest) {
    this.manifest = manifest;
  }
}
