/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public interface CanLoadBalance {
  void attach(KubernetesManifest loadBalancer, KubernetesManifest target);

  List<JsonPatch> detachPatch(KubernetesManifest loadBalancer, KubernetesManifest target);

  List<JsonPatch> attachPatch(KubernetesManifest loadBalancer, KubernetesManifest target);

  static CanLoadBalance lookupProperties(
      ResourcePropertyRegistry registry, Pair<KubernetesKind, String> name) {
    KubernetesResourceProperties loadBalancerProperties = registry.get(name.getLeft());

    KubernetesHandler loadBalancerHandler = loadBalancerProperties.getHandler();
    if (loadBalancerHandler == null) {
      throw new IllegalArgumentException(
          "No handler registered for " + name + ", are you sure it's a valid load balancer type?");
    }

    if (!(loadBalancerHandler instanceof CanLoadBalance)) {
      throw new IllegalArgumentException(
          "No support for load balancing via " + name + " exists in Spinnaker");
    }

    return (CanLoadBalance) loadBalancerHandler;
  }
}
