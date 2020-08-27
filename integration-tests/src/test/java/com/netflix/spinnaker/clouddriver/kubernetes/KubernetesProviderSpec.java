/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.is;

import com.netflix.spinnaker.clouddriver.Main;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {Main.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location = classpath:/kubernetes/clouddriver.yml"})
@Testcontainers
public class KubernetesProviderSpec {

  @LocalServerPort int port;

  @Container public static KubernetesCluster cluster1 = KubernetesCluster.getInstance("account1");
  @Container public static KubernetesCluster cluster2 = KubernetesCluster.getInstance("account2");

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @Test
  public void accountsAreRegistered() {
    Response response = get(baseUrl() + "/credentials");
    response.prettyPrint();
    response.then().assertThat().statusCode(200).body("size()", is(2));
  }
}
