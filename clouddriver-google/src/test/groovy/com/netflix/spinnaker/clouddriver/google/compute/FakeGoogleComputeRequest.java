package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;
import java.io.IOException;

public class FakeGoogleComputeRequest<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    implements GoogleComputeRequest<RequestT, ResponseT> {

  private final ResponseT response;

  private boolean executed = false;

  public FakeGoogleComputeRequest(ResponseT response) {
    this.response = response;
  }

  @Override
  public ResponseT execute() throws IOException {
    executed = true;
    return response;
  }

  @Override
  public RequestT getRequest() {
    throw new UnsupportedOperationException("FakeGoogleComputeRequest#getRequest()");
  }

  public boolean executed() {
    return executed;
  }
}
