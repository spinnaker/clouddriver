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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Subnet
import com.netflix.spinnaker.clouddriver.model.SubnetProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/subnets")
@RestController
class SubnetController {

  @Autowired
  List<SubnetProvider> subnetProviders

  @Autowired
  MessageSource messageSource

  @ExceptionHandler
  @ResponseStatus(HttpStatus.NOT_FOUND)
  Map handleSubnetNotFoundException(SubnetNotFoundException ex) {
    def message = messageSource.getMessage("subnet.not.found", [ex.provider, ex.id] as String[], "subnet.not.found", LocaleContextHolder.locale)
    [error: "subnet.not.found", message: message, status: HttpStatus.NOT_FOUND]
  }

  static class SubnetNotFoundException extends RuntimeException {
    String provider
    String id
  }

  @RequestMapping(method = RequestMethod.GET)
  Set<Subnet> list() {
    subnetProviders.collectMany {
      it.all
    } as Set
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}")
  Set<Subnet> listByCloudProvider(@PathVariable String cloudProvider) {
    subnetProviders.findAll { subnetProvider ->
      subnetProvider.type == cloudProvider
    } collectMany {
      it.all
    }
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}/{subnetId}")
  Subnet getByProviderAndId(@PathVariable String cloudProvider, @PathVariable String subnetId) {
    Subnet subnet = listByCloudProvider(cloudProvider).find { it.id == subnetId }
    if (!subnet) {
      throw new SubnetNotFoundException(provider: cloudProvider, id: subnetId)
    }

    subnet
  }

  // TODO: implement the rest
}
