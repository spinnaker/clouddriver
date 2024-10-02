/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model

import spock.lang.Specification

class GoogleDiskSpec extends Specification {

  def "Test setting disk type pd-ssd"() {
    given: "A GoogleDisk object"
    def disk = new GoogleDisk()

    when: "The type is set"
    disk.setType("pd-ssd")

    then: "The type should be pd-ssd"
    disk.type == GoogleDiskType.PD_SSD
  }

  def "Test setting disk type hyperdisk-balanced"() {
    given: "A GoogleDisk object"
    def disk = new GoogleDisk()

    when: "The type is set"
    disk.setType("hyperdisk-balanced")

    then: "The type should be hyperdisk-balanced"
    disk.type == GoogleDiskType.HYPERDISK_BALANCED
  }

  def "Test default disk type on unknown"() {
    given: "A GoogleDisk object"
    def disk = new GoogleDisk()

    when: "The type is set"
    disk.setType("UNKNOWN")

    then: "The type should be pd-standard"
    disk.type == GoogleDiskType.PD_STANDARD
  }

  def "Test persistent disk detection"() {
    given: "A persistent GoogleDisk object"
    def disk = new GoogleDisk(type: "pd-ssd")

    expect: "The disk is detected as persistent"
    disk.isPersistent()
  }
}
