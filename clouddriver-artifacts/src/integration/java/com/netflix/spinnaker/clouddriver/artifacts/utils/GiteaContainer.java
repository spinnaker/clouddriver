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

package com.netflix.spinnaker.clouddriver.artifacts.utils;

import static io.restassured.RestAssured.given;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

public class GiteaContainer extends GenericContainer<GiteaContainer> {

  private static final String DOCKER_IMAGE = "gitea/gitea:1.12.6";
  private static final String REPO_NAME = "test";
  private static final String USER = "test";
  private static final String PASS = "test";

  public GiteaContainer() {
    super(DOCKER_IMAGE);
    withExposedPorts(3000, 22)
        .withCopyFileToContainer(MountableFile.forClasspathResource("gitea_data"), "/data");
  }

  @Override
  public void start() {
    super.start();
    initRepo();
  }

  public String httpUrl() {
    return "http://localhost:3000/" + USER + "/" + REPO_NAME + ".git";
  }

  public String sshUrl() {
    return "git@localhost:" + USER + "/" + REPO_NAME + ".git";
  }

  private void initRepo() {
    String baseUrl = "http://" + this.getContainerIpAddress() + ":" + this.getMappedPort(3000);

    Map<String, Object> body =
        ImmutableMap.of("auto_init", true, "name", REPO_NAME, "private", true);
    Response resp =
        given()
            .auth()
            .preemptive()
            .basic(USER, PASS)
            .contentType("application/json")
            .body(body)
            .post(baseUrl + "/api/v1/user/repos");
    resp.then().statusCode(201);
  }
}
