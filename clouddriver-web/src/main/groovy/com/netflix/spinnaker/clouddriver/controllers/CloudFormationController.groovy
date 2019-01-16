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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.CloudFormation
import com.netflix.spinnaker.clouddriver.model.CloudFormationProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.rest.webmvc.ResourceNotFoundException
import org.springframework.web.bind.annotation.*

@Slf4j
@RequestMapping("/cloudFormation")
@RestController
class CloudFormationController {

  @Autowired
  List<CloudFormationProvider> cloudFormationProviders

  @RequestMapping(method = RequestMethod.GET, value = "/list/{accountId}")
  List<CloudFormation> list(@PathVariable String accountId,
                            @RequestParam(required = false, defaultValue = '*') String region) {
    log.debug("Cloud formation list stacks for account $accountId")
    cloudFormationProviders.collect {
      it.list(accountId, region)
    }.flatten()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/get")
  CloudFormation get(@RequestParam String stackId) {
    log.debug("Cloud formation get stack with id $stackId")
    try {
      cloudFormationProviders.collect {
        it.get(stackId)
      }.first()
    } catch (NoSuchElementException e) {
      throw new ResourceNotFoundException("Cloud Formation stackId $stackId not found.")
    }
  }

}
