/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import org.openstack4j.api.OSClient
import org.openstack4j.api.exceptions.ServerResponseException
import org.openstack4j.api.identity.v3.IdentityService
import org.openstack4j.api.identity.v3.RegionService
import org.openstack4j.model.identity.v3.Region
import org.springframework.http.HttpStatus
import spock.lang.Specification
import spock.lang.Unroll


@Unroll
class OpenstackClientV3ProviderSpec extends Specification {
  OpenstackClientV3Provider provider
  OSClient.OSClientV3 mockClient

  def "setup"() {
    mockClient = Mock(OSClient.OSClientV3)
    provider = new OpenstackClientV3Provider(mockClient) {
      @Override
      OSClient getClient() {
        mockClient
      }

      OSClient getRegionClient(String region) {
        mockClient
      }
    }
  }

  def "test get regions lookup"() {
    given:
    IdentityService identityService = Mock()
    RegionService regionService = Mock()
    Region region = Mock()
    String regionId = UUID.randomUUID().toString()
    List<? extends Region> regions = [region]

    when:
    List<String> result = provider.getAllRegions()

    then:
    1 * mockClient.identity() >> identityService
    1 * identityService.regions() >> regionService
    1 * regionService.list() >> regions
    1 * region.id >> regionId
    result == [regionId]
    noExceptionThrown()
  }

  def "test get regions lookup exception"() {
    given:
    IdentityService identityService = Mock()
    RegionService regionService = Mock()
    Throwable throwable = new ServerResponseException('foo', HttpStatus.INTERNAL_SERVER_ERROR.value())

    when:
    provider.getAllRegions()

    then:
    1 * mockClient.identity() >> identityService
    1 * identityService.regions() >> regionService
    1 * regionService.list() >> { throw throwable }

    and:
    OpenstackProviderException openstackProviderException = thrown(OpenstackProviderException)
    openstackProviderException.cause == throwable
  }
}
