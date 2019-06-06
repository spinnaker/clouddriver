package com.netflix.spinnaker.clouddriver.google.compute;

import java.io.IOException;

public interface GoogleComputeRequest<T> {

  T execute() throws IOException;
}
