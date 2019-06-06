package com.netflix.spinnaker.clouddriver.google.compute;

import static java.util.stream.Collectors.toList;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.services.compute.ComputeRequest;
import com.google.common.collect.ImmutableList;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor;
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class GoogleComputeRequestImpl<T> implements GoogleComputeRequest<T> {

  private final ComputeRequest<T> request;
  private final Registry registry;
  private final String metricName;
  private final Map<String, String> tags;

  GoogleComputeRequestImpl(
      ComputeRequest<T> request, Registry registry, String metricName, Map<String, String> tags) {
    this.request = request;
    this.registry = registry;
    this.metricName = metricName;
    this.tags = tags;
  }

  @Override
  public T execute() throws IOException {
    return timeExecute(request);
  }

  private T timeExecute(AbstractGoogleClientRequest<T> request) throws IOException {
    return GoogleExecutor.timeExecute(
        registry, request, "google.api", metricName, getTimeExecuteTags(request));
  }

  private String[] getTimeExecuteTags(AbstractGoogleClientRequest<?> request) {
    String account = AccountForClient.getAccount(request.getAbstractGoogleClient());
    return ImmutableList.<String>builder()
        .add("account")
        .add(account)
        .addAll(flattenTags())
        .build()
        .toArray(new String[] {});
  }

  private List<String> flattenTags() {
    return tags.entrySet().stream()
        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
        .collect(toList());
  }
}
