/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.validators;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("stopCloudFoundryServerGroupDescriptionValidator")
public class StopCloudFoundryServerGroupDescriptionValidator
    extends AbstractCloudFoundryDescriptionValidator {}
