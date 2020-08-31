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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

import io.restassured.response.Response;

public class ForceCacheRefreshRequest extends BaseRequest<Response> {

  private String account;
  private String location;
  private String name;

  public ForceCacheRefreshRequest(String baseUrl) {
    super(baseUrl);
  }

  public ForceCacheRefreshRequest withAccount(String account) {
    this.account = account;
    return this;
  }

  public ForceCacheRefreshRequest withLocation(String location) {
    this.location = location;
    return this;
  }

  public ForceCacheRefreshRequest withName(String name) {
    this.name = name;
    return this;
  }

  public String getAccount() {
    return account;
  }

  public String getLocation() {
    return location;
  }

  public String getName() {
    return name;
  }

  @Override
  public Response executeAndValidate() {
    Response resp =
        given()
            .contentType("application/json")
            .body(this)
            .post(baseUrl + "/cache/kubernetes/manifest");

    resp.then().log().ifValidationFails().statusCode(anyOf(is(200), is(202)));
    return resp;
  }
}
