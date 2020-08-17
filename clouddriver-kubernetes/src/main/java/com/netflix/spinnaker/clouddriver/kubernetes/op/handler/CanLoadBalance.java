/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.ResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface CanLoadBalance {
  void attach(KubernetesManifest loadBalancer, KubernetesManifest target);

  List<JsonPatch> detachPatch(KubernetesManifest loadBalancer, KubernetesManifest target);

  List<JsonPatch> attachPatch(KubernetesManifest loadBalancer, KubernetesManifest target);

  static CanLoadBalance lookupProperties(
      ResourcePropertyRegistry registry, KubernetesCoordinates coords) {
    KubernetesHandler loadBalancerHandler = registry.get(coords.getKind()).getHandler();
    if (!(loadBalancerHandler instanceof CanLoadBalance)) {
      throw new IllegalArgumentException(
          "No support for load balancing via " + coords.getKind() + " exists in Spinnaker");
    }

    return (CanLoadBalance) loadBalancerHandler;
  }
}
