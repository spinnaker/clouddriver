package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

class ComputeOperationMockHttpTransport extends HttpTransport {

  private final MockLowLevelHttpResponse createOperationResponse;

  ComputeOperationMockHttpTransport(MockLowLevelHttpResponse createOperationResponse) {
    this.createOperationResponse = createOperationResponse;
  }

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String url) {
    if (url.toLowerCase().contains("operation")) {
      return new MockLowLevelHttpRequest(url)
          .setResponse(
              new MockLowLevelHttpResponse()
                  .setStatusCode(200)
                  .setContent(
                      "" + "{" + "  \"name\":   \"opName\"," + "  \"status\": \"DONE\"" + "}"));
    } else {
      return new MockLowLevelHttpRequest(url).setResponse(createOperationResponse);
    }
  }
}
