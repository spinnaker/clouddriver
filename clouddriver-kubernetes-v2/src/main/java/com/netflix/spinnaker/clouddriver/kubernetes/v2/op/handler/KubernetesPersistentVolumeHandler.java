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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler.DeployPriority.MOUNTABLE_DATA_BACKING_RESOURCE_PRIORITY;

import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesCoreCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent.KubernetesV2CachingAgentFactory;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.Manifest.Status;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class KubernetesPersistentVolumeHandler extends KubernetesHandler {
  @Override
  public int deployPriority() {
    return MOUNTABLE_DATA_BACKING_RESOURCE_PRIORITY.getValue();
  }

  @Nonnull
  @Override
  public KubernetesKind kind() {
    return KubernetesKind.PERSISTENT_VOLUME;
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
  public Status status(KubernetesManifest manifest) {
    return new Status();
  }

  @Override
  protected KubernetesV2CachingAgentFactory cachingAgentFactory() {
    return KubernetesCoreCachingAgent::new;
  }
}
