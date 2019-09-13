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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DELETE_MANIFEST;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesDeleteManifestOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.Map;
import org.springframework.stereotype.Component;

@KubernetesOperation(DELETE_MANIFEST)
@Component
public class KubernetesDeleteManifestConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesDeleteManifestOperation(convertDescription(input));
  }

  @Override
  public KubernetesDeleteManifestDescription convertDescription(Map input) {
    return KubernetesAtomicOperationConverterHelper.convertDescription(
        input, this, KubernetesDeleteManifestDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
