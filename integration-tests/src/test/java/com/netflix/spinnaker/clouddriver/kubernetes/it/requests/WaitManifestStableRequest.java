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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.restassured.response.Response;
import java.util.concurrent.TimeUnit;

public class WaitManifestStableRequest extends BaseRequest<Void> {

  private String namespace;
  private String account;
  private String objectName;

  public WaitManifestStableRequest(String baseUrl) {
    super(baseUrl);
  }

  public WaitManifestStableRequest withAccount(String account) {
    this.account = account;
    return this;
  }

  public WaitManifestStableRequest withNamespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public WaitManifestStableRequest withObjectName(String objectName) {
    this.objectName = objectName;
    return this;
  }

  @Override
  public Void executeAndValidate() throws InterruptedException {
    repeatUntilTrue(
        () -> {
          Response resp =
              given()
                  .queryParam("includeEvents", false)
                  .get(baseUrl + "/manifests/" + account + "/" + namespace + "/" + objectName);
          resp.then()
              .log()
              .ifValidationFails()
              .statusCode(200)
              .body("status.failed.state", is(false));
          return resp.jsonPath().getBoolean("status.stable.state");
        },
        5,
        TimeUnit.MINUTES,
        "Waited 5 minutes on GET /manifest.. to return \"status.stable.state: true\"");
    return null;
  }
}
