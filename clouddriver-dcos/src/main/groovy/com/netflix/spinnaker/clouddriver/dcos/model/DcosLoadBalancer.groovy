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

package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup

class DcosLoadBalancer implements LoadBalancer, Serializable {
  final String type = Keys.PROVIDER
  final String cloudProvider = Keys.PROVIDER
  String name
  String account
  Set<LoadBalancerServerGroup> serverGroups = [] as Set

  DcosLoadBalancer(String name, String account) {
    this.name = name
    this.account = account
  }

  DcosLoadBalancer(List<DcosServerGroup> serverGroupList, String accountName) {
    this.name = ""
    this.account = accountName
//    this.serverGroups = serverGroupList?.collect { serverGroup ->
//      new LoadBalancerServerGroup(
//        name: serverGroup?.name,
//        isDisabled: serverGroup?.isDisabled(),
//        instances: serverGroup?.instances?.findResults { instance ->
//          if (instance.isAttached(this.name)) {
//            return new LoadBalancerInstance(
//                id: instance.name,
//                zone: null,
//                health: [
//                    state: instance.healthState.toString()
//                ]
//            )
//          } else {
//            return (LoadBalancerInstance) null // Groovy generics need to be convinced all control flow paths return the same object type
//          }
//        } as Set,
//        detachedInstances: serverGroup?.instances?.findResults { instance ->
//          if (!instance.isAttached(this.name)) {
//            return instance.name
//          } else {
//            return (String) null
//          }
//        } as Set)
//    } as Set
  }
}
