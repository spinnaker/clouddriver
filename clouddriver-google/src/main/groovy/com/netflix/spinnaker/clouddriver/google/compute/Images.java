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
import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.ImageList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import java.io.IOException;

public class Images {

  public static final ImmutableMap<String, String> TAGS =
      ImmutableMap.of(GoogleExecutor.getTAG_SCOPE(), GoogleExecutor.getSCOPE_GLOBAL());

  private final GoogleNamedAccountCredentials credentials;
  private final Registry registry;

  public Images(GoogleNamedAccountCredentials credentials, Registry registry) {
    this.credentials = credentials;
    this.registry = registry;
  }

  public GoogleComputeRequest<Compute.Images.List, ImageList> list(String project)
      throws IOException {

    Compute.Images.List request = credentials.getCompute().images().list(project);
    return wrapRequest(request, "list");
  }

  private <RequestT extends ComputeRequest<ResponseT>, ResponseT>
      GoogleComputeRequest<RequestT, ResponseT> wrapRequest(RequestT request, String api) {
    return new GoogleComputeRequestImpl<>(request, registry, getMetricName(api), TAGS);
  }

  private String getMetricName(String api) {
    return "compute.images." + api;
  }
}
