/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesJobStatus
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.JobProvider
import com.netflix.spinnaker.clouddriver.model.JobState
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesJobProvider implements JobProvider<KubernetesJobStatus> {
  String platform = "kubernetes"
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  KubernetesJobProvider() { }

  @Override
  KubernetesJobStatus collectJob(String account, String location, String id) {
    def credentials = accountCredentialsProvider.getCredentials(account).credentials
    if (!credentials instanceof KubernetesNamedAccountCredentials) {
      return null
    }
    def trueCredentials = credentials as KubernetesNamedAccountCredentials
    def status = new KubernetesJobStatus(trueCredentials.apiAdaptor.getPod(location, id), account)
    if (status.jobState in [JobState.Failed, JobState.Succeeded]) {
      trueCredentials.apiAdaptor.deletePod(location, id)
    }
    return status
  }

  @Override
  Map<String, Object> getFileContents(String account, String location, String id, String fileName){
    return [:]
  }

}
