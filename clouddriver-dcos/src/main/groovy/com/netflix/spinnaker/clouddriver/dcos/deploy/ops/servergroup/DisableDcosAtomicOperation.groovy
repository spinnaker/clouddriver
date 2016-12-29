/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DcosServerGroupDescription

class DisableDcosAtomicOperation extends AbstractEnableDisableDcosAtomicOperation {
  @Override
  final String getBasePhase() {
    'DISABLE'
  }

  @Override
  final String getAction() {
    'false'
  }

  @Override
  final String getVerb() {
    'disabling'
  }

  DisableDcosAtomicOperation(DcosServerGroupDescription description) {
    super(description)
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "disableServerGroup": { "serverGroupName": "kub-test-v000", "account": "my-dcos-account", "namespace": "default" } } ]' localhost:7002/dcos/ops
   */
  @Override
  Void operate(List priorOutputs) {
    super.operate(priorOutputs)
  }
}
