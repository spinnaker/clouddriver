/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.netflix.spinnaker.clouddriver.aws.model.AmazonBlockDevice

class BlockDeviceConfig {

  static def List<AmazonBlockDevice> enumeratedBlockDevicesWithVirtualName(int size) {
    def letters = ('a'..'z').collect { it }
    (0..<size).collect {
      def letter = letters[it + 1]
      new AmazonBlockDevice(deviceName: "/dev/sd${letter}", virtualName: "ephemeral${it}")
    }
  }

  static def defaultBlockDevicesForEbsOnly()  {
    [
      new AmazonBlockDevice(deviceName: "/dev/sdb", size: 125),
      new AmazonBlockDevice(deviceName: "/dev/sdc", size: 125),
    ]
  }

  static final def blockDevicesByInstanceType = [
    "t2.micro" : [],
    "t2.small" : [],
    "t2.medium" : [],
    "t2.large" : [],
    "m4.large" : [],
    "m4.xlarge" : [],
    "m4.2xlarge" : [],
    "m4.4xlarge" : [],
    "m4.10xlarge" : [],
    "c4.large" : [],
    "c4.xlarge" : [],
    "c4.2xlarge" : [],
    "c4.4xlarge" : [],
    "c4.8xlarge" : [],
    "c3.large": [],
    "c3.xlarge": [],
    "c3.2xlarge": [],
    "c3.4xlarge": [],
    "c3.8xlarge": [],
    "m3.medium" : [],
    "m3.large" : [],
    "m3.xlarge" : [],
    "m3.2xlarge" : [],
    "r3.large": [],
    "r3.xlarge": [],
    "r3.2xlarge": [],
    "r3.4xlarge": [],
    "r3.8xlarge": [],
    "i2.xlarge" : [],
    "i2.2xlarge" : [],
    "i2.4xlarge" : [],
    "i2.8xlarge" : [],
    "d2.xlarge" : [],
    "d2.2xlarge" : [],
    "d2.4xlarge" : [],
    "d2.8xlarge" : [],
  ].asImmutable()

}
