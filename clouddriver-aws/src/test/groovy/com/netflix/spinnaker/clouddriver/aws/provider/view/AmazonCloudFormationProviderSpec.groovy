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

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.clouddriver.aws.cache.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormation
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CLOUDFORMATION

class AmazonCloudFormationProviderSpec extends Specification {
  static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  @Subject
  AmazonCloudFormationProvider provider

  ObjectMapper objectMapper = new ObjectMapper()

  def setup() {
    def cache = new InMemoryCache()
    cloudFormations.each {
      cache.merge(CLOUDFORMATION.ns,
          new DefaultCacheData(makeKey(it), objectMapper.convertValue(it, ATTRIBUTES), [:]))
    }

    provider = new AmazonCloudFormationProvider(cache, objectMapper)
  }

  @Unroll
  void "list all cloud formations by account (any region)"() {
    when:
    def result = provider.list(accountId, '*') as Set

    then:
    result == cloudFormations.findAll { it.accountId == accountId } as Set

    where:
    accountId  || count
    "account1" || 2
    "account2" || 1
    "unknown"  || 0
    null       || 0
  }

  @Unroll
  void "list all cloud formations by account and region"() {
    when:
    def result = provider.list(account, region) as Set

    then:
    result == cloudFormations.findAll { it.accountId == account && it.region == region} as Set

    where:
    account     | region      || count
    "account1"  | "region1"   || 1
    "account1"  | "region2"   || 1
    "account1"  | "region3"   || 0
    "account1"  | null        || 0
    "account2"  | "region1"   || 1
    "unknown"   | "unknown"   || 0
  }

  @Unroll
  void "get a cloud formation by stackId"() {
    when:
    def result = provider.get(stackId)

    then:
    result == cloudFormations.find { it.stackId == stackId }

    where:
    stackId    || count
    "stack1"   || 1
    "stack2"   || 1
    "stack3"   || 1
  }

  void "throws a NoSuchElementException if stackId doesn't exist"() {
    when:
    provider.get(stackId)

    then:
    thrown(expectedException)

    where:
    stackId   || expectedException
    "unknown" || NoSuchElementException
    null      || NoSuchElementException
  }

  @Shared
  Set<AmazonCloudFormation> cloudFormations = [
          new AmazonCloudFormation(stackId: "stack1", region: "region1", accountId: "account1"),
          new AmazonCloudFormation(stackId: "stack2", region: "region2", accountId: "account1"),
          new AmazonCloudFormation(stackId: "stack3", region: "region1", accountId: "account2")
  ]

  private static String makeKey(AmazonCloudFormation stack) {
    Keys.getCloudFormationKey(stack.stackId, stack.region, stack.accountId)
  }

}
