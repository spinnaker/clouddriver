/*
 * Copyright 2021 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.KubernetesCachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesDestinationRuleHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    return KubernetesHandler.DeployPriority.MOUNTABLE_DATA_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.DESTINATION_RULE;
  }

  @Override
  public boolean versioned() {
    return false;
  }

  @Nonnull
  @Override
  public SpinnakerKind spinnakerKind() {
    return SpinnakerKind.CONFIGS;
  }

  @Override
  public Manifest.Status status(KubernetesManifest manifest) {
    return Manifest.Status.defaultStatus();
  }

  @Override
  protected KubernetesCachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
