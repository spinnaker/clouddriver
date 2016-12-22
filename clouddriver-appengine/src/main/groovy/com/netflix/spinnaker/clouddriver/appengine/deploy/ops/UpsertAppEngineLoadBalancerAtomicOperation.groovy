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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.TrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertAppEngineLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertAppEngineLoadBalancerDescription description

  @Autowired
  AppEngineLoadBalancerProvider appEngineLoadBalancerProvider

  UpsertAppEngineLoadBalancerAtomicOperation(UpsertAppEngineLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "loadBalancerName": "default", "credentials": "my-appengine-account", "migrateTraffic": false, "split": { "shardBy": "COOKIE" } } } ]' localhost:7002/appengine/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "loadBalancerName": "default", "credentials": "my-appengine-account", "migrateTraffic": false, "split": { "shardBy": "IP", "allocations": { "app-stack-detail-v000": "0.5", "app-stack-detail-v001": "0.5" } } } } ]' localhost:7002/appengine/ops
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.loadBalancerName " +
      "in $description.credentials.region..."

    def credentials = description.credentials
    def appengine = credentials.appengine
    def loadBalancerName = description.loadBalancerName
    def updateSplit = description.split

    // TODO(dpeach): do validation for traffic migration.
    def migrateTraffic = description.migrateTraffic

    def ancestorLoadBalancer = appEngineLoadBalancerProvider.getLoadBalancer(credentials.name, loadBalancerName)
    def override = copyAndOverrideAncestorSplit(ancestorLoadBalancer.split, updateSplit)

    def service = new Service(
      split: new TrafficSplit(
        allocations: override.allocations,
        shardBy: override.shardBy ? override.shardBy.toString() : null
      )
    )

    appengine.apps().services().patch(credentials.project, loadBalancerName, service)
      .setUpdateMask("split")
      .setMigrateTraffic(migrateTraffic)
      .execute()

    task.updateStatus BASE_PHASE, "Done upserting $loadBalancerName in $description.credentials.region."
    [loadBalancers: [(credentials.region): [name: loadBalancerName]]]
  }

  static AppEngineTrafficSplit copyAndOverrideAncestorSplit(AppEngineTrafficSplit ancestor, AppEngineTrafficSplit update) {
    AppEngineTrafficSplit override = ancestor.clone()

    if (!update) {
      return ancestor
    }

    if (update.allocations) {
      override.allocations = update.allocations
    }

    if (update.shardBy) {
      override.shardBy = update.shardBy
    }

    override
  }
}
