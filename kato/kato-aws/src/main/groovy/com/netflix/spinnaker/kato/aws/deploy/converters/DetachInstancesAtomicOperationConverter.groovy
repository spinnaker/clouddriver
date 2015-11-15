/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.aws.deploy.converters

import com.netflix.spinnaker.kato.aws.deploy.description.DetachInstancesDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.DetachInstancesAtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.stereotype.Component

@Component("detachInstancesDescription")
class DetachInstancesAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  AtomicOperation convertOperation(Map input) {
    new DetachInstancesAtomicOperation(convertDescription(input))
  }

  @Override
  DetachInstancesDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, DetachInstancesDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
