/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters

import com.netflix.spinnaker.clouddriver.appengine.AppengineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.UpsertAppengineLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineModelUtil
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@AppengineOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component
class UpsertAppengineLoadBalancerAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  AtomicOperation convertOperation(Map input) {
    new UpsertAppengineLoadBalancerAtomicOperation(convertDescription(input))
  }

  UpsertAppengineLoadBalancerDescription convertDescription(Map input) {
    UpsertAppengineLoadBalancerDescription description = AppengineAtomicOperationConverterHelper
      .convertDescription(input, this, UpsertAppengineLoadBalancerDescription) as UpsertAppengineLoadBalancerDescription

    if (description.splitDescription) {
      description.split = AppengineModelUtil.convertTrafficSplitDescriptionToTrafficSplit(description.splitDescription)
    }

    return description
  }
}
