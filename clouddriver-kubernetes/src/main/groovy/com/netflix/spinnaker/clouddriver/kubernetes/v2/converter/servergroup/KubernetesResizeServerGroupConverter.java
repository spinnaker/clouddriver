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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.servergroup;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.servergroup.KubernetesResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.servergroup.KubernetesResizeServerGroupOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RESIZE_SERVER_GROUP;

@KubernetesOperation(RESIZE_SERVER_GROUP)
@Component
public class KubernetesResizeServerGroupConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  private KubernetesResourcePropertyRegistry registry;

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesResizeServerGroupOperation(convertDescription(input), registry);
  }

  @Override
  public KubernetesResizeServerGroupDescription convertDescription(Map input) {
    return (KubernetesResizeServerGroupDescription) KubernetesAtomicOperationConverterHelper
        .convertDescription(input, this, KubernetesDeployManifestDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}

