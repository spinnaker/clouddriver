package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.model.Operation;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.io.IOException;

public interface GoogleComputeOperationRequest extends GoogleComputeRequest<Operation> {

  Operation executeAndWait(Task task, String phase) throws IOException;
}
