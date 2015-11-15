/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.mort.web

import com.netflix.spinnaker.mort.model.Network
import com.netflix.spinnaker.mort.model.NetworkProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@Deprecated
@RequestMapping("/vpcs")
@RestController
class VpcController {

  @Autowired
  List<NetworkProvider> networkProviders

  @RequestMapping(method = RequestMethod.GET)
  Set<Network> list() {
    networkProviders.findAll { networkProvider ->
      // Using the 'aws' constant directly here to avoid auto-wiring an AmazonCloudProvider.
      networkProvider.cloudProvider == 'aws'
    } collectMany {
      it.all
    }
  }
}
