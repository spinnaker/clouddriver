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

package com.netflix.spinnaker.clouddriver.aws.model.edda

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable

@Immutable
@CompileStatic
@EqualsAndHashCode(cache = true)
class InstanceLoadBalancerState {
  String instanceId
  String loadBalancerName
  String state
  String reasonCode
  String description

  static List<InstanceLoadBalancerState> fromLoadBalancerInstanceState(LoadBalancerInstanceState lbis) {
    lbis.instances.collect { new InstanceLoadBalancerState(loadBalancerName: lbis.name, instanceId: it.instanceId, state: it.state, reasonCode: it.reasonCode, description: it.description)}
  }
}
