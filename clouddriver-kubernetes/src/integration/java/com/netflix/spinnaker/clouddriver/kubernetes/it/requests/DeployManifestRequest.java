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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.kubernetes.it.helpers.TestManifest;
import io.restassured.response.Response;
import java.util.*;

public class DeployManifestRequest extends BaseRequest<String> {

  public Map<String, Object> deployManifest = new HashMap<>();

  @JsonIgnore private TestManifest manifest;
  @JsonIgnore private String namespace;
  @JsonIgnore private String account;
  @JsonIgnore private String application;

  public DeployManifestRequest(String baseUrl) {
    super(baseUrl);
  }

  public DeployManifestRequest withManifest(TestManifest manifest) {
    this.manifest = manifest;
    return this;
  }

  public DeployManifestRequest withNamespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public DeployManifestRequest withAccount(String account) {
    this.account = account;
    return this;
  }

  public DeployManifestRequest withSpinApplication(String application) {
    this.application = application;
    return this;
  }

  private List<DeployManifestRequest> buildRequest() {
    List<Map<String, Object>> manifestContents = manifest.getContents();

    deployManifest.put("cloudProvider", "kubernetes");
    deployManifest.put("enableTraffic", true);
    deployManifest.put("account", account);
    deployManifest.put("namespaceOverride", namespace);
    HashMap<Object, Object> moniker = new HashMap<>();
    moniker.put("app", application);
    deployManifest.put("moniker", moniker);
    deployManifest.put("source", "text");
    deployManifest.put("skipExpressionEvaluation", false);
    deployManifest.put("requiredArtifacts", new ArrayList<>());
    deployManifest.put("manifests", manifestContents);

    return Collections.singletonList(this);
  }

  @Override
  public String executeAndValidate() {
    Response resp =
        given()
            .contentType("application/json")
            .body(buildRequest())
            .post(baseUrl + "/kubernetes/ops");

    resp.then().log().ifValidationFails().statusCode(200);

    return resp.jsonPath().get("id");
  }
}
