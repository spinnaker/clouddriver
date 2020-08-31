/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.it.requests;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.restassured.response.Response;
import java.util.concurrent.TimeUnit;

public class WaitForTaskRequest extends BaseRequest<Void> {

  @JsonIgnore private String taskId;

  public WaitForTaskRequest(String baseUrl) {
    super(baseUrl);
  }

  public WaitForTaskRequest withTaskId(String namespace) {
    this.taskId = namespace;
    return this;
  }

  @Override
  public Void executeAndValidate() throws InterruptedException {
    repeatUntilTrue(
        () -> {
          Response resp = get(baseUrl + "/task/" + taskId);
          if (resp.statusCode() == 404) {
            return false;
          }
          resp.then().log().ifValidationFails().statusCode(200);
          resp.then().log().ifValidationFails().body("status.failed", is(false));
          return resp.jsonPath().getBoolean("status.completed");
        },
        30,
        TimeUnit.SECONDS,
        "Waited 30 seconds on GET /task/{id} to return \"status.completed: true\"");
    return null;
  }
}
