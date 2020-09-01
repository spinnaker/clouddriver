/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.converter.job;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RUN_JOB;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.description.job.KubernetesRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubernetesRunJobDeploymentResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubernetesRunJobOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@KubernetesOperation(RUN_JOB)
@Component
public class KubernetesRunJobOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  private final ResourceVersioner resourceVersioner;
  private final boolean appendSuffix;

  @Autowired
  public KubernetesRunJobOperationConverter(
      ResourceVersioner resourceVersioner,
      @Value("${kubernetes.jobs.append-suffix:false}") boolean appendSuffix) {
    this.resourceVersioner = resourceVersioner;
    this.appendSuffix = appendSuffix;
  }

  @Override
  public AtomicOperation<KubernetesRunJobDeploymentResult> convertOperation(
      Map<String, Object> input) {
    return new KubernetesRunJobOperation(
        convertDescription(input), resourceVersioner, appendSuffix);
  }

  @Override
  public KubernetesRunJobOperationDescription convertDescription(Map<String, Object> input) {
    return KubernetesAtomicOperationConverterHelper.convertDescription(
        input, this, KubernetesRunJobOperationDescription.class);
  }
}
