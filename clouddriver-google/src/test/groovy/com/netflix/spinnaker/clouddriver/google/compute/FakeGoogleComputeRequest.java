/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.compute;

import com.google.api.services.compute.ComputeRequest;

public class FakeGoogleComputeRequest<RequestT extends ComputeRequest<ResponseT>, ResponseT>
    implements GoogleComputeRequest<RequestT, ResponseT> {

  private final RequestT request;
  private final ResponseT response;

  private boolean executed = false;

  public FakeGoogleComputeRequest(ResponseT response) {
    this(null, response);
  }

  public FakeGoogleComputeRequest(RequestT request, ResponseT response) {
    this.request = request;
    this.response = response;
  }

  @Override
  public ResponseT execute() {
    executed = true;
    return response;
  }

  @Override
  public RequestT getRequest() {
    if (request == null) {
      throw new UnsupportedOperationException("FakeGoogleComputeRequest#getRequest()");
    }
    return request;
  }

  public boolean executed() {
    return executed;
  }
}
