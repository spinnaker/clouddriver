/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import spock.lang.Specification
import spock.lang.Unroll

class GoogleServerGroupCachingAgentSpec extends Specification {
  private static final String BUILD_HOST = "http://some-jenkins-host:8080/"

  def "should not set build info if no image description is found"() {
    setup:
      GoogleServerGroup googleServerGroup = new GoogleServerGroup()

    when:
      GoogleZonalServerGroupCachingAgent.extractBuildInfo(null, googleServerGroup)

    then:
      !googleServerGroup.buildInfo

    when:
      GoogleZonalServerGroupCachingAgent.extractBuildInfo("", googleServerGroup)

    then:
      !googleServerGroup.buildInfo
  }

  def "should not set build info if no relevant image description is found"() {
    setup:
      GoogleServerGroup googleServerGroup = new GoogleServerGroup()

    when:
      GoogleZonalServerGroupCachingAgent.extractBuildInfo("Some non-appversion image description...", googleServerGroup)

    then:
      !googleServerGroup.buildInfo

    when:
      GoogleZonalServerGroupCachingAgent.extractBuildInfo("SomeKey1: SomeValue1, SomeKey2: SomeValue2", googleServerGroup)

    then:
      !googleServerGroup.buildInfo
  }

  def "should set build host if image description contains appversion and build_host"() {
    setup:
      GoogleServerGroup googleServerGroup = new GoogleServerGroup()

    when:
      GoogleZonalServerGroupCachingAgent.extractBuildInfo(
        "appversion: somepackage-1.0.0-586499.h150/WE-WAPP-somepackage/150, build_host: $BUILD_HOST",
        googleServerGroup)

    then:
      with(googleServerGroup.buildInfo) {
        package_name == "somepackage"
        version == "1.0.0"
        commit == "586499"
        jenkins == [
          name: "WE-WAPP-somepackage",
          number: "150",
          host: BUILD_HOST
        ]
      }
  }

  @Unroll
  def "should sort disks so boot disk is first persistent disk"() {
    setup:
      def launchConfig = [instanceTemplate: [properties: [disks: disks]]]
      GoogleServerGroup googleServerGroup = new GoogleServerGroup(launchConfig: launchConfig)

    when:
      GoogleZonalServerGroupCachingAgent.sortWithBootDiskFirst(googleServerGroup)

    then:
      googleServerGroup.launchConfig.instanceTemplate.properties.disks == sortedWithBootFirst

    where:
      disks                                                                                                                                                                                                       || sortedWithBootFirst
      [[boot: true, type: 'PERSISTENT']]                                                                                                                                                                          || [[boot: true, type: 'PERSISTENT']]
      [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2']]                                                                                           || [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: true, type: 'PERSISTENT', source: 'disk-url-2']]                                                                                           || [[boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1']]
      [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]                                  || [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]                                  || [[boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]

      // Mix in a SCRATCH disk.
      [[boot: true, type: 'PERSISTENT'], [boot: false, type: 'SCRATCH']]                                                                                                                                          || [[boot: true, type: 'PERSISTENT'], [boot: false, type: 'SCRATCH']]
      [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH']]                                                           || [[boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH']]                                                           || [[boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'SCRATCH']]
      [[boot: false, type: 'SCRATCH'], [boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]  || [[boot: false, type: 'SCRATCH'], [boot: true, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]  || [[boot: true, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]

      // Boot disk missing (really shouldn't happen, but want to ensure we don't disturb the results).
      [[boot: false, type: 'PERSISTENT']]                                                                                                                                                                         || [[boot: false, type: 'PERSISTENT']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2']]                                                                                          || [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]                                 || [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]

      // Mix in a SCRATCH disk and Boot disk missing.
      [[boot: false, type: 'PERSISTENT'], [boot: false, type: 'SCRATCH']]                                                                                                                                         || [[boot: false, type: 'PERSISTENT'], [boot: false, type: 'SCRATCH']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH']]                                                          || [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH']]
      [[boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']] || [[boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]
      [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']] || [[boot: false, type: 'PERSISTENT', source: 'disk-url-1'], [boot: false, type: 'PERSISTENT', source: 'disk-url-2'], [boot: false, type: 'SCRATCH'], [boot: false, type: 'PERSISTENT', source: 'disk-url-3']]
  }

  @Unroll
  def "malformed instance properties shouldn't break disk sorting logic"() {
    setup:
      def launchConfig = [instanceTemplate: instanceTemplate]
      GoogleServerGroup googleServerGroup = new GoogleServerGroup(launchConfig: launchConfig)

    when:
      GoogleZonalServerGroupCachingAgent.sortWithBootDiskFirst(googleServerGroup)

    then:
      googleServerGroup.launchConfig.instanceTemplate == instanceTemplate

    where:
      instanceTemplate << [
        null,
        [properties: null],
        [properties: [:]],
        [properties: [disks: null]],
        [properties: [disks: []]]
      ]
  }
}
