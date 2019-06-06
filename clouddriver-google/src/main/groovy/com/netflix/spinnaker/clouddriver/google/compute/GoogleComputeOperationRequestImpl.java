package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.io.IOException;
import java.util.Map;

final class GoogleComputeOperationRequestImpl extends GoogleComputeRequestImpl<Operation>
    implements GoogleComputeOperationRequest {

  @FunctionalInterface
  interface OperationWaiter {
    Operation wait(Operation operation, Task task, String phase);
  }

  private final OperationWaiter operationWaiter;

  GoogleComputeOperationRequestImpl(
      ComputeRequest<Operation> request,
      Registry registry,
      String metricName,
      Map<String, String> tags,
      OperationWaiter operationWaiter) {
    super(request, registry, metricName, tags);
    this.operationWaiter = operationWaiter;
  }

  @Override
  public Operation executeAndWait(Task task, String phase) throws IOException {
    return operationWaiter.wait(execute(), task, phase);
  }
}
