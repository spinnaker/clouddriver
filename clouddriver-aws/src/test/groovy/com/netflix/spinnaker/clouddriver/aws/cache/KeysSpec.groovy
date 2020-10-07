/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.cache

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def 'key fields match namespace fields if present'() {

    expect:
    Keys.parse(key).keySet() == namespace.fields

    where:

    key                                                                                                         | namespace
    "aws:securityGroups:appname:appname-stack-detail:test:us-west-1:appname-stack-detail-v000:stack:detail:000" | Keys.Namespace.SECURITY_GROUPS
  }

  @Unroll
  def 'decode escaped security group name'() {

    expect:
    Keys.parse(key)

    where:

    key                                                                              || name
    "aws:securityGroups:app-stack-detail:sg-12345:us-west-2:0123456789:vpc-1234"     || 'appname-stack-detail'
    "aws:securityGroups:app%3Astack%25detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'appname:stack%detail'
  }

  @Unroll
  def 'encode security group name'() {

    expect:
    key == Keys.getSecurityGroupKey(securityGroupName, securityGroupId, region, account, vpcId)

    where:

    securityGroupName  | securityGroupId | region      | account      | vpcId      || key
    "app-stack-detail" | "sg-12345"      | "us-west-2" | "0123456789" | "vpc-1234" || "aws:securityGroups:app-stack-detail:sg-12345:us-west-2:0123456789:vpc-1234"
    "app:stack%detail" | "sg-12345"      | "us-west-2" | "0123456789" | "vpc-1234" || "aws:securityGroups:app%3Astack%25detail:sg-12345:us-west-2:0123456789:vpc-1234"
  }
}
