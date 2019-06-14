package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.io.IOException;

public class FakeGoogleComputeOperationRequest<RequestT extends ComputeRequest<Operation>>
    extends FakeGoogleComputeRequest<RequestT, Operation>
    implements GoogleComputeOperationRequest<RequestT> {

  private boolean waited = false;

  public FakeGoogleComputeOperationRequest(Operation response) {
    super(response);
  }

  @Override
  public Operation executeAndWait(Task task, String phase) throws IOException {
    waited = true;
    return execute();
  }

  public boolean waitedForCompletion() {
    return waited;
  }
}
