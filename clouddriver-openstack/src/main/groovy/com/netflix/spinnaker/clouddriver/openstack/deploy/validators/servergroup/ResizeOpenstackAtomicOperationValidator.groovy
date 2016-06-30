/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.openstack.OpenstackOperation
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ResizeOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.OpenstackAttributeValidator
import com.netflix.spinnaker.clouddriver.openstack.deploy.validators.AbstractOpenstackDescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@OpenstackOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component
class ResizeOpenstackAtomicOperationValidator extends AbstractOpenstackDescriptionValidator<ResizeOpenstackAtomicOperationDescription> {

  String context = "resizeOpenstackServerGroupAtomicOperationDescription"

  @Override
  void validate(OpenstackAttributeValidator validator, List priorDescriptions, ResizeOpenstackAtomicOperationDescription description, Errors errors) {
    validator.validateNotEmpty(description.serverGroupName, "serverGroupName")
    validator.validateNotNull(description.capacity, "capacity")
    validator.validatePositive(description.capacity.min, "capacity.min")
    validator.validateGreaterThanEqual(description.capacity.max, description.capacity.min, "capacity.max")
  }

}
