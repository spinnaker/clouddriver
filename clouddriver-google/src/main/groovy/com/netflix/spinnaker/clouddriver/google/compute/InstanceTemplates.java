/*
 * Copyright 2019 Google, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.InstanceTemplate;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public class InstanceTemplates {

  private final GoogleNamedAccountCredentials credentials;
  private final GlobalGoogleComputeRequestFactory requestFactory;

  InstanceTemplates(
      GoogleNamedAccountCredentials credentials,
      GoogleOperationPoller operationPoller,
      Registry registry) {
    this.credentials = credentials;
    this.requestFactory =
        new GlobalGoogleComputeRequestFactory(
            "instanceTemplates", credentials, operationPoller, registry);
  }

  public GoogleComputeOperationRequest<Compute.InstanceTemplates.Delete> delete(String name)
      throws IOException {

    Compute.InstanceTemplates.Delete request =
        credentials.getCompute().instanceTemplates().delete(credentials.getProject(), name);
    return requestFactory.wrapOperationRequest(request, "delete");
  }

  public GoogleComputeRequest<Compute.InstanceTemplates.Get, InstanceTemplate> get(String name)
      throws IOException {
    Compute.InstanceTemplates.Get request =
        credentials.getCompute().instanceTemplates().get(credentials.getProject(), name);
    return requestFactory.wrapRequest(request, "get");
  }

  public GoogleComputeOperationRequest<Compute.InstanceTemplates.Insert> insert(
      InstanceTemplate template) throws IOException {
    Compute.InstanceTemplates.Insert request =
        credentials.getCompute().instanceTemplates().insert(credentials.getProject(), template);
    return requestFactory.wrapOperationRequest(request, "insert");
  }
}
