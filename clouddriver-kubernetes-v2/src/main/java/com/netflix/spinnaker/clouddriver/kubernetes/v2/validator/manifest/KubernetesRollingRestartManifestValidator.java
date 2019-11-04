/*
 * Copyright 2019 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.validator.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.ROLLING_RESTART_MANIFEST;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesRollingRestartManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.validator.KubernetesValidationUtil;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@KubernetesOperation(ROLLING_RESTART_MANIFEST)
@Component
public class KubernetesRollingRestartManifestValidator
    extends DescriptionValidator<KubernetesRollingRestartManifestDescription> {
  private final AccountCredentialsProvider provider;

  @Autowired
  public KubernetesRollingRestartManifestValidator(AccountCredentialsProvider provider) {
    this.provider = provider;
  }

  @Override
  public void validate(
      List priorDescriptions,
      KubernetesRollingRestartManifestDescription description,
      Errors errors) {
    KubernetesValidationUtil util =
        new KubernetesValidationUtil("rollingRestartKubernetesManifest", errors);
    if (!util.validateV2Credentials(
        provider,
        description.getAccount(),
        description.getPointCoordinates().getKind(),
        description.getPointCoordinates().getNamespace())) {
      return;
    }
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
