/*
 * Copyright 2016 Target, Inc.
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


package com.netflix.spinnaker.clouddriver.openstack.health

import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification


class OpenstackHealthIndicatorSpec extends Specification {


  def "health fails when openstack appears unreachable"() {
    given:

    def mocked_provider = GroovyMock(OpenstackClientProvider) {
      getTokenId() >> { throw new IOException("fail") }
    }

    def mocked_os_creds = GroovyMock(OpenstackCredentials) {
      getProvider() >> { mocked_provider }
    }


    def credentials = GroovyMock(OpenstackNamedAccountCredentials) {
      getCredentials() >> { mocked_os_creds }
    }

    def credential_provider = GroovyMock(AccountCredentialsProvider) {
      getAll() >> [credentials]
      getCredentials(_) >> { credentials }
    }

    def indicator = new OpenstackHealthIndicator(accountCredentialsProvider: credential_provider)

    when:
    indicator.checkHealth()
    indicator.health()

    then:
    thrown(OpenstackHealthIndicator.OpenstackIOException)

  }

  def "health succeeds when openstack is reachable"() {
    given:

    def mocked_provider = GroovyMock(OpenstackClientProvider) {
      getTokenId() >> { "success" }
    }

    def mocked_os_creds = GroovyMock(OpenstackCredentials) {
      getProvider() >> { mocked_provider }
    }


    def credentials = GroovyMock(OpenstackNamedAccountCredentials) {
      getCredentials() >> { mocked_os_creds }
    }

    def credential_provider = GroovyMock(AccountCredentialsProvider) {
      getAll() >> [credentials]
      getCredentials(_) >> { credentials }
    }

    def indicator = new OpenstackHealthIndicator(accountCredentialsProvider: credential_provider)

    when:
    indicator.checkHealth()

    then:
    indicator.health().status == Status.UP

  }

}
