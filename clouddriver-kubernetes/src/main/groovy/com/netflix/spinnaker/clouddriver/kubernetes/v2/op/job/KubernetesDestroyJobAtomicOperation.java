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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.job.KubernetesJobDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import io.kubernetes.client.models.V1DeleteOptions;
import java.util.List;

public class KubernetesDestroyJobAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY";
  private final KubernetesJobDescription description;

  public KubernetesDestroyJobAtomicOperation(KubernetesJobDescription jobDescription) {
    this.description = jobDescription;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = getTask();
    task.updateStatus(BASE_PHASE, "Initializing destroy of job.");
    KubernetesNamedAccountCredentials namedAccountCredentials = description.getCredentials();

    if (!(namedAccountCredentials.getCredentials() instanceof KubernetesV2Credentials)) {
      throw new IllegalArgumentException("Only V2 credentials allowed");
    }

    KubernetesV2Credentials credentials =
        (KubernetesV2Credentials) namedAccountCredentials.getCredentials();

    task.updateStatus(BASE_PHASE, "Destroying job...");
    credentials.delete(
        KubernetesKind.JOB,
        description.getNamespace(),
        description.getJobName(),
        new KubernetesSelectorList(),
        new V1DeleteOptions());
    task.updateStatus(
        BASE_PHASE,
        "Successfully destroyed job "
            + description.getJobName()
            + " in namespace "
            + description.getNamespace());
    return null;
  }
}
