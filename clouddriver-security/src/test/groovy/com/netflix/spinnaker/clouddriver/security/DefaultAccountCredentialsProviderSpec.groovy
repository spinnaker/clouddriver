/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.security.TestUtils.buildGoogleNamedAccountCredentials
import static com.netflix.spinnaker.clouddriver.security.TestUtils.buildNetflixAmazonCredentials

class DefaultAccountCredentialsProviderSpec extends Specification {

  @Shared AccountCredentialsRepository repo
  @Subject AccountCredentialsProvider provider

  def setup() {
    repo = Mock(AccountCredentialsRepository)
    provider = new DefaultAccountCredentialsProvider(repo)
  }

  void "should call repo to retrieve objects"() {
    when:
      provider.getCredentials(key)

    then:
      1 * repo.getOne(key)

    when:
      provider.getAll()

    then:
      1 * repo.getAll() >> [new TestAccountCredentials(name: "disabledAccount", enabled: true)]

    where:
      key = "foo"
  }

  @Unroll
  def "getAll(#includeAll) should return correct account credentials"() {
    when:
    def credentials = provider.getAll(includeAll)

    then:
      1 * repo.getAll() >> [
        buildGoogleNamedAccountCredentials("googleAccount"),
        new TestAccountCredentials(name: "disabledAccount", enabled: false),
        buildNetflixAmazonCredentials("awsAccount")
      ]

    expect:
      credentials*.name as Set == expected as Set

    where:
      includeAll | expected
      false      | ["googleAccount", "awsAccount"]
      true       | ["googleAccount", "awsAccount", "disabledAccount"]
  }

  private class TestAccountCredentials implements AccountCredentials<TestAccountCredentials> {

    String name
    String environment
    String accountType
    boolean enabled
    String cloudProvider
    List<String> requiredGroupMembership

    @Override
    TestAccountCredentials getCredentials() {
      return this
    }
  }
}
