package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.services.compute.ComputeRequest;
import java.io.IOException;

public interface ComputeBatchRequest<RequestT extends ComputeRequest<ResponseT>, ResponseT> {

  void queue(
      GoogleComputeRequest<RequestT, ResponseT> request, JsonBatchCallback<ResponseT> callback);

  void execute(String batchContext) throws IOException;
}
