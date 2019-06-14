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
