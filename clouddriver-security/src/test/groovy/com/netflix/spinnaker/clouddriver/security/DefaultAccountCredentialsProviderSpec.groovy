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

import com.netflix.spinnaker.security.User
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultAccountCredentialsProviderSpec extends Specification {

  @Shared AccountCredentialsRepository repo
  @Subject AccountCredentialsProvider provider

  def setup() {
    repo = Mock(AccountCredentialsRepository)
    provider = new DefaultAccountCredentialsProvider(repo)
    SecurityContextHolder.clearContext()
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  void "should call repo to retrieve objects"() {
    when:
      provider.getCredentials(key)

    then:
      1 * repo.getOne(key)

    when:
      provider.getAll()

    then:
      1 * repo.getAll()

    where:
      key = "foo"
  }

  def "should not return accounts to users not in requiredGroupMembership from getAll"() {
    setup:
      AccountCredentials openCreds = Mock(AccountCredentials) {
        getRequiredGroupMembership() >> []
      }
      AccountCredentials closedCreds = Mock(AccountCredentials) {
        getRequiredGroupMembership() >> ["VIPs"]
      }
      repo.getAll() >> [openCreds, closedCreds]

    when:
      Set results = provider.all

    then:
      results.size() == 1
      results.first() == openCreds

    when:
      userWithAccounts(["VIPs"])
      results = provider.all

    then:
      results.size() == 2
      results.contains(openCreds)
      results.contains(closedCreds)

    when:
      userWithAccounts(["someOtherVIPs"])
      results = provider.all

    then:
      results.size() == 1
      results.first() == openCreds
  }

  @Unroll
  def "should not return accounts to users not in requiredGroupMembership from getCredentials"() {
    setup:
      AccountCredentials creds = Mock(AccountCredentials) {
        getRequiredGroupMembership() >> reqMembership
      }
      repo.getOne("abc") >> creds

    when:
      userWithAccounts(allowedAccounts)
      def result = provider.getCredentials("abc")

    then:
      // Groovy won't let me return creds in the data table for some reason.
      returnCreds ? result == creds : result == null

    where:
      reqMembership | allowedAccounts || returnCreds
      []            | []              || true
      []            | ["a"]           || true
      ["a"]         | []              || false
      ["a"]         | ["b"]           || false
      ["a"]         | ["a"]           || true
  }

  def "should deny access if no credential is found"() {
    setup:
      repo.getOne("abc") >> null // happens by default, but just to be clear.

    when:
      def result = provider.getCredentials("abc")

    then:
      result == null
  }

  def userWithAccounts(List accounts) {
    def ctx = SecurityContextHolder.createEmptyContext()
    ctx.setAuthentication(new TestingAuthenticationToken(new User(allowedAccounts: accounts), null))
    SecurityContextHolder.setContext(ctx)
  }
}
