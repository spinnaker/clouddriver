/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryJobStatus;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.model.JobProvider;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Map;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryJobProvider implements JobProvider<CloudFoundryJobStatus> {

  @Getter private String platform = CloudFoundryCloudProvider.ID;
  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository;

  public CloudFoundryJobProvider(
      CredentialsRepository<CloudFoundryCredentials> credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public CloudFoundryJobStatus collectJob(String account, String location, String id) {
    CloudFoundryCredentials credentials = credentialsRepository.getOne(account);
    if (credentials == null) {
      return null;
    }

    Task task = credentials.getClient().getTasks().getTask(id);
    return CloudFoundryJobStatus.fromTask(task, account, location);
  }

  @Override
  public Map<String, Object> getFileContents(
      String account, String location, String id, String fileName) {
    return null;
  }

  @Override
  public void cancelJob(String account, String location, String taskGuid) {
    CloudFoundryCredentials credentials = credentialsRepository.getOne(account);
    if (credentials == null) {
      return;
    }
    credentials.getClient().getTasks().cancelTask(taskGuid);
  }
}
