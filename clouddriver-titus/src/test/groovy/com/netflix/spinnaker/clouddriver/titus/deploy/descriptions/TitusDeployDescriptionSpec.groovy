/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.descriptions

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.titus.client.model.MigrationPolicy
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import spock.lang.Specification
import spock.lang.Unroll

class TitusDeployDescriptionSpec extends Specification {

  @Unroll
  def "ser/de"() {
    given:
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()

    and:
    TitusDeployDescription subject = new TitusDeployDescription(
      account: "titustest",
      region: "us-east-1",
      application: "helloworld",
      capacity: new TitusDeployDescription.Capacity(
        desired: 1,
        max: 1,
        min: 1
      ),
      capacityGroup: "helloworld",
      containerAttributes: [:],
      credentials: credentials,
      env: [:],
      hardConstraints: [],
      iamProfile: "helloworldInstanceProfile",
      imageId: "titus/helloworld:latest",
      inService: true,
      labels: [:],
      migrationPolicy: new MigrationPolicy(
        type: "systemDefault"
      ),
      resources: new TitusDeployDescription.Resources(
        allocateIpAddress: true,
        cpu: 2,
        disk: 10000,
        memory: 4096,
        networkMbps: 128
      ),
      securityGroups: [],
      softConstraints: []
    )

    when:
    objectMapper.readValue(objectMapper.writeValueAsString(subject), TitusDeployDescription)

    then:
    noExceptionThrown()

    where:
    credentials << [
      null,
      Mock(NetflixTitusCredentials) {
        getName() >> "titustest"
      }
    ]
  }
}
