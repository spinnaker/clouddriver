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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ResizeAsgAtomicOperation
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component("resizeAsgDescription")
@AmazonOperation(AtomicOperations.RESIZE_SERVER_GROUP)
class ResizeAsgAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new ResizeAsgAtomicOperation(convertDescription(input))
  }

  @Override
  ResizeAsgDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, ResizeAsgDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }

  /**
   * Converts legacy resize asg descriptions to their modern equivalent.
   *
   * Expects to be run after {@see AsgNameToServerGroupNameDescriptionPreProcessor}.
   */
  @Order(0)
  @Component
  static class ResizeAsgDescriptionPreProcessor implements AtomicOperationDescriptionPreProcessor {
    @Override
    boolean supports(Class descriptionClass, Map description) {
      return descriptionClass == ResizeAsgDescription
    }

    @Override
    Map process(Map description) {
      if (description.asgs) {
        return description
      }

      description.asgs = description.regions.collect {
        [serverGroupName: description.serverGroupName, region: it, capacity: description.capacity]
      }

      description.remove("serverGroupName")
      description.remove("asgName")
      description.remove("regions")
      description.remove("capacity")
      return description
    }
  }

}
