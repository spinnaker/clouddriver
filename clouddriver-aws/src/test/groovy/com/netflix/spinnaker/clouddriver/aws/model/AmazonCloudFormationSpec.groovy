/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.clouddriver.aws.model

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class AmazonCloudFormationSpec extends Specification {

  def objectMapper = new ObjectMapper()

  def "should deserialize a full cached object"() {
    given:
    def attributes = [
      stackId: "stackId",
      tags: [tag1: "tag1", tag2: "tag2"],
      outputs: [out1: "out1", out2: "out2"],
      stackName: "stackName",
      region: "region",
      accountName: "accountName",
      accountId: "accountId",
      stackStatus: "stackStatus",
      stackStatusReason: "stackStatusReason"
    ]

    when:
    def cf = objectMapper.convertValue(attributes, AmazonCloudFormation)

    then:
    assert cf instanceof AmazonCloudFormation
    cf.stackId == "stackId"
    cf.tags == [tag1: "tag1", tag2: "tag2"]
    cf.outputs == [out1: "out1", out2: "out2"]
    cf.stackName == "stackName"
    cf.region == "region"
    cf.accountName == "accountName"
    cf.accountId == "accountId"
    cf.stackStatus == "stackStatus"
    cf.stackStatusReason == "stackStatusReason"
  }

  def "should deserialize object with missing fields"() {
    given:
    def attributes = [stackId: "stackId"]

    when:
    def cf = objectMapper.convertValue(attributes, AmazonCloudFormation)

    then:
    assert cf instanceof AmazonCloudFormation
    cf.stackId == "stackId"
    cf.tags == null
    cf.outputs == null
    cf.stackName == null
    cf.region == null
    cf.accountName == null
    cf.accountId == null
    cf.stackStatus == null
    cf.stackStatusReason == null
  }

}
