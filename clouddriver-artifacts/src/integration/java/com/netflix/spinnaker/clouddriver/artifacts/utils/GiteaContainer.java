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
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.ImmutableMap;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.testcontainers.containers.GenericContainer;

public class GiteaContainer extends GenericContainer<GiteaContainer> {

  private static final String DOCKER_IMAGE = "gitea/gitea:1.12.6";
  private static final String REPO_NAME = "test";
  private static final String USER = "test";
  private static final String PASS = "test";

  public GiteaContainer() {
    super(DOCKER_IMAGE);
    withExposedPorts(3000, 22);
  }

  @Override
  public void start() {
    super.start();
    String baseUrl = "http://" + this.getContainerIpAddress() + ":" + this.getMappedPort(3000);
    try {
      createUser(baseUrl);
      String token = createToken(baseUrl);
      System.setProperty("gitea_token", token);
      createSshKey(baseUrl);
      createRepo(baseUrl);
      Path knownHosts = generateKnownHosts();
      System.setProperty("known_hosts", knownHosts.toString());
    } catch (IOException | InterruptedException e) {
      fail("Exception initializing gitea: " + e.getMessage());
    }
  }

  public String httpUrl() {
    return "http://localhost:3000/" + USER + "/" + REPO_NAME + ".git";
  }

  public String sshUrl() {
    return "git@localhost:" + USER + "/" + REPO_NAME + ".git";
  }

  private void createUser(String baseUrl) {
    String formBody =
        "db_type=SQLite3&db_host=localhost%3A3306&db_user=root&db_passwd=&db_name=gitea&ssl_mode=disable&db_schema=&"
            + "charset=utf8&db_path=%2Fdata%2Fgitea%2Fgitea.db&app_name=Gitea%3A+Git+with+a+cup+of+tea&"
            + "repo_root_path=%2Fdata%2Fgit%2Frepositories&lfs_root_path=%2Fdata%2Fgit%2Flfs&"
            + "run_user=git&domain=localhost&ssh_port=22&http_port=3000&app_url=http%3A%2F%2Flocalhost%3A3000%2F&"
            + "log_root_path=%2Fdata%2Fgitea%2Flog&smtp_host=&smtp_from=&smtp_user=&smtp_passwd=&"
            + "enable_federated_avatar=on&enable_open_id_sign_in=on&enable_open_id_sign_up=on&"
            + "default_allow_create_organization=on&default_enable_timetracking=on&"
            + "no_reply_address=noreply.localhost"
            + "&admin_name="
            + USER
            + "&admin_passwd="
            + PASS
            + "&admin_confirm_passwd="
            + PASS
            + "&admin_email=test@test.com";

    given()
        .contentType("application/x-www-form-urlencoded")
        .body(formBody)
        .post(baseUrl + "/install")
        .then()
        .statusCode(302);
  }

  private String createToken(String baseUrl) {
    Map<String, Object> body = ImmutableMap.of("name", "test_token");

    Response resp =
        given()
            .auth()
            .preemptive()
            .basic(USER, PASS)
            .contentType("application/json")
            .body(body)
            .post(baseUrl + "/api/v1/users/" + USER + "/tokens");
    resp.prettyPrint();
    resp.then().statusCode(201);
    return resp.jsonPath().getString("sha1");
  }

  private void createSshKey(String baseUrl) throws IOException {
    Map<String, Object> body =
        ImmutableMap.of(
            "key",
            IOUtils.resourceToString("/ssh/id_rsa_test.pub", Charset.defaultCharset()),
            "read_only",
            true,
            "title",
            "test_key");

    Response resp =
        given()
            .auth()
            .preemptive()
            .basic(USER, PASS)
            .contentType("application/json")
            .body(body)
            .post(baseUrl + "/api/v1/admin/users/" + USER + "/keys");
    resp.then().statusCode(201);
  }

  private void createRepo(String baseUrl) {
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

  private Path generateKnownHosts() throws IOException, InterruptedException {
    ExecResult execResult =
        execInContainer("ssh-keygen -y -f /data/ssh/ssh_host_rsa_key".split(" "));
    String publicKey = execResult.getStdout();
    Path knownHosts = Paths.get(System.getenv("BUILD_DIR"), "ssh", "known_hosts");
    if (knownHosts.toFile().getParentFile().exists()) {
      FileUtils.forceDelete(knownHosts.toFile().getParentFile());
      FileUtils.forceMkdir(knownHosts.toFile().getParentFile());
    }
    FileUtils.writeStringToFile(
        knownHosts.toFile(), "localhost " + publicKey, Charset.defaultCharset());
    return knownHosts;
  }
}
