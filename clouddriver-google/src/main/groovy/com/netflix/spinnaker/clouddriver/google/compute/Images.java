package com.netflix.spinnaker.clouddriver.google.compute;

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

  public GoogleComputeRequest<ImageList> list(String project) throws IOException {

    ComputeRequest<ImageList> request = credentials.getCompute().images().list(project);
    return wrapRequest(request, "list");
  }

  private <T> GoogleComputeRequest<T> wrapRequest(ComputeRequest<T> request, String api) {
    return new GoogleComputeRequestImpl<>(request, registry, getMetricName(api), TAGS);
  }

  private String getMetricName(String api) {
    return "compute.images." + api;
  }
}
