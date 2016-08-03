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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.SerializeApplicationAtomicOperation

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.SerializeApplicationDescription.DeserializeApplicationDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.beans.factory.annotation.Autowired


class DeserializeApplicationAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESERIALIZE_APPLICATION"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeserializeApplicationDescription description
  private final String applicationName
  private final String accountName
  private String project
  private List applicationTags

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  DeserializeApplicationAtomicOperation(DeserializeApplicationDescription description) {
    this.description = description
    this.applicationName = description.applicationName
    this.accountName = description.accountName
    this.applicationTags = []
  }

  /* curl -X POST -H "Content-Type: application/json" -d '[ { "deserializeApplication": { "applicationName": "example", "credentials": "my-google-account" }} ]' localhost:7002/gce/ops */
  @Override
  Void operate(List priorOutputs) {
    //TODO(nwwebb) change name from serialize and deserialize to saveSnapshot and restoreSnapshot
    def credentials = accountCredentialsRepository.getOne(this.accountName) as GoogleNamedAccountCredentials
    this.project = credentials.project

    def stateMap = [:]
    task.updateStatus BASE_PHASE, "Restoring server groups for the application ${this.applicationName} in account ${this.accountName}"
    googleClusterProvider.getClusters(applicationName, accountName).each { GoogleCluster.View cluster ->
      cluster.serverGroups.each { GoogleServerGroup.View serverGroup ->
        addServerGroupToStateMap(serverGroup, stateMap)
      }
    }

    task.updateStatus BASE_PHASE, "Restoring load balancers for the application ${this.applicationName} in account ${this.accountName}"
    googleLoadBalancerProvider.getApplicationLoadBalancers(applicationName).each { GoogleLoadBalancerView loadBalancer ->
      if (loadBalancer.account == this.accountName) {
        addLoadBalancerToStateMap(loadBalancer, stateMap)
      }
    }

    task.updateStatus BASE_PHASE, "Restoring security groups for application ${this.applicationName} in account ${this.accountName}"
    googleSecurityGroupProvider.getAll(true).each { GoogleSecurityGroup securityGroup ->
      if (securityGroup.accountName == this.accountName && securityGroup.targetTags && !Collections.disjoint(securityGroup.targetTags, applicationTags)) {
        addSecurityGroupToStateMap(securityGroup, stateMap)
      }
    }

    return null
  }

  private Void addServerGroupToStateMap(GoogleServerGroup.View serverGroup, Map stateMap) {
    //TODO(nwwebb) restore server groups
    return null
  }

  private Void addLoadBalancerToStateMap(GoogleLoadBalancerView loadBalancer, Map stateMap) {
    //TODO(nwwebb) restore load balancers
    return null
  }

  private Void addSecurityGroupToStateMap(GoogleSecurityGroup securityGroup, Map stateMap) {
    //TODO(nwwebb) restore security groups
    return null
  }
}
