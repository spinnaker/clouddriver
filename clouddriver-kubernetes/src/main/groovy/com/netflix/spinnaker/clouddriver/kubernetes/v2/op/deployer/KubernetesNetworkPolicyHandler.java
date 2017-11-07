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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesNetworkPolicyCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import org.springframework.stereotype.Component;

@Component
public class KubernetesNetworkPolicyHandler extends KubernetesHandler implements CanDelete {
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.NETWORK_POLICY;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.SECURITY_GROUP;
  }

  @Override
  public Status status(KubernetesManifest manifest) {
    return Status.stable();
  }

  @Override
  public Class<? extends KubernetesV2CachingAgent> cachingAgentClass() {
    return KubernetesNetworkPolicyCachingAgent.class;
  }
}
