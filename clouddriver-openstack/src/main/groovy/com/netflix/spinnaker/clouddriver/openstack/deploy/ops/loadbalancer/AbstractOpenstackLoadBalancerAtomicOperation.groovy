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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.MemberData
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.StackPoolMemberAware
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.ServerGroupConstants
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.openstack.networking.domain.ext.ListItem

abstract class AbstractOpenstackLoadBalancerAtomicOperation implements TaskStatusAware, StackPoolMemberAware, LoadBalancerResolver {

  OpenstackCredentials openstackCredentials

  AbstractOpenstackLoadBalancerAtomicOperation(OpenstackCredentials openstackCredentials) {
    this.openstackCredentials = openstackCredentials
  }

  /**
   * Update the server group to remove the given load balancer.
   * @param loadBalancerId
   */
  void updateServerGroup(String operation, String region, String loadBalancerId, List<String> loadBalancersToDelete = []) {
    task.updateStatus operation, "Updating server groups that reference load balancer $loadBalancerId..."
    provider.listStacksWithLoadBalancers(region, [loadBalancerId]).each { stackSummary ->
      //get stack details
      task.updateStatus operation, "Fetching stack details for server group $stackSummary.name..."
      Stack stack = provider.getStack(region, stackSummary.name)
      task.updateStatus operation, "Fetched stack details for server group $stackSummary.name."

      //update parameters
      ServerGroupParameters newParams = ServerGroupParameters.fromParamsMap(stack.parameters)
      if (loadBalancersToDelete) {
        newParams.loadBalancers.removeAll(loadBalancersToDelete)
      }

      //get the current template from the stack
      task.updateStatus operation, "Fetching current template for server group $stack.name..."
      String template = provider.getHeatTemplate(region, stack.name, stack.id)
      task.updateStatus operation, "Successfully fetched current template for server group $stack.name."

      //we need to store subtemplate in asg output from create, as it is required to do an update and there is no native way of
      //obtaining it from a stack
      task.updateStatus operation, "Fetching subtemplates for server group $stack.name..."
      List<Map<String, Object>> outputs = stack.outputs
      String subtemplate = outputs.find { m -> m.get("output_key") == ServerGroupConstants.SUBTEMPLATE_OUTPUT }.get("output_value")

      //rebuild memberTemplate
      String memberTemplate = buildPoolMemberTemplate(newParams.loadBalancers.collectMany { lbid ->
        task.updateStatus operation, "Looking up load balancer details for load balancer $lbid..."
        LoadBalancerV2 loadBalancer = provider.getLoadBalancer(region, lbid)
        task.updateStatus operation, "Found load balancer details for load balancer $lbid."
        loadBalancer.listeners.collect { item ->
          task.updateStatus operation, "Looking up load balancer listener details for listener $item.id..."
          ListenerV2 listener = provider.getListener(region, item.id)
          String internalPort = parseListenerKey(listener.description).internalPort
          String poolId = listener.defaultPoolId
          task.updateStatus operation, "Found load balancer listener details (poolId=$poolId, internalPort=$internalPort) for listener $item.id."
          new MemberData(subnetId: loadBalancer.vipSubnetId, externalPort: listener.protocolPort.toString(), internalPort: internalPort, poolId: poolId)
        }
      })
      task.updateStatus operation, "Fetched subtemplates for server group $stack.name."

      //update stack
      task.updateStatus operation, "Updating server group $stack.name..."
      provider.updateStack(region, stack.name, stack.id, template, [(ServerGroupConstants.SUBTEMPLATE_FILE): subtemplate, (ServerGroupConstants.MEMBERTEMPLATE_FILE): memberTemplate], newParams, newParams.loadBalancers)
      task.updateStatus operation, "Successfully updated server group $stack.name."
    }

    task.updateStatus operation, "Updated server groups that reference load balancer $loadBalancerId."
  }

  /**
   * Removes load balancer listeners/pools/monitor associated with load balancer.
   * @param operation
   * @param region
   * @param loadbalancerId
   * @param listenerStatuses
   */
  protected void deleteLoadBalancerPeripherals(String operation, String region, String loadBalancerId, Collection<ListenerV2> listeners) {
    BlockingStatusChecker blockingActiveStatusChecker = createBlockingActiveStatusChecker(region, loadBalancerId)
    //remove elements
    listeners?.each { ListenerV2 currentListener ->
      try {
        LbPoolV2 lbPool = provider.getPool(region, currentListener.defaultPoolId)
        if (lbPool.healthMonitorId) {
          removeHealthMonitor(operation, region, loadBalancerId, lbPool.healthMonitorId)
        }
        //delete pool
        task.updateStatus operation, "Deleting pool $lbPool.id on listener $currentListener.id in $region ..."
        blockingActiveStatusChecker.execute { provider.deletePool(region, lbPool.id) }
        task.updateStatus operation, "Deleted pool $lbPool.id on listener $currentListener.id in $region."

      } catch (OpenstackResourceNotFoundException ope) {
        // Do nothing.
      }

      //delete listener
      task.updateStatus operation, "Deleting listener $currentListener.id on load balancer $loadBalancerId in $region..."
      blockingActiveStatusChecker.execute { provider.deleteListener(region, currentListener.id) }
      task.updateStatus operation, "Deleted listener $currentListener.id on load balancer $loadBalancerId in $region."
    }
  }

  /**
   * Shared method to remove a health monitor given its ID.
   * @param operation
   * @param region
   * @param id
   */
  protected void removeHealthMonitor(String operation, String region, String loadBalancerId, String id) {
    task.updateStatus operation, "Removing existing monitor ${id} in ${region}..."
    createBlockingActiveStatusChecker(region, loadBalancerId).execute { provider.deleteMonitor(region, id) }
    task.updateStatus operation, "Removed existing monitor ${id} in ${region}."
  }

  /**
   * Creates and returns a new blocking active status checker.
   * @param region
   * @param loadBalancerId
   * @return
   */
  BlockingStatusChecker createBlockingActiveStatusChecker(String region, String loadBalancerId = null) {
    OpenstackConfigurationProperties.LbaasConfig config = this.openstackCredentials.credentials.lbaasConfig
    BlockingStatusChecker.from(config.pollTimeout, config.pollInterval) { Object input ->
      String id = loadBalancerId
      if (!loadBalancerId && input && input instanceof LoadBalancerV2) {
        id = ((LoadBalancerV2) input).id
      }

      LbProvisioningStatus currentProvisioningStatus = provider.getLoadBalancer(region, id)?.provisioningStatus

      // Short circuit polling if openstack is unable to provision the load balancer
      if (LbProvisioningStatus.ERROR == currentProvisioningStatus) {
        throw new OpenstackProviderException("Openstack was unable to provision load balancer ${loadBalancerId}")
      }

      LbProvisioningStatus.ACTIVE == currentProvisioningStatus
    }
  }

  /**
   * Checks to see if the load balancer is in a pending state.
   * @param loadBalancer
   */
  protected void checkPendingLoadBalancerState(LoadBalancerV2 loadBalancer) {
    if (loadBalancer.provisioningStatus.name().contains('PENDING')) {
      throw new OpenstackOperationException(AtomicOperations.DELETE_LOAD_BALANCER, "Load balancer $loadBalancer.id must not be in PENDING provisioning status to be deleted. Current status is $loadBalancer.provisioningStatus")
    }
  }

  /**
   * Helper method to lookup listeners associated to load balancers into a map by listener key.
   * @param region
   * @param loadBalancer
   * @return
   */
  protected Map<String, ListenerV2> buildListenerMap(String region, LoadBalancerV2 loadBalancer) {
    loadBalancer?.listeners?.collectEntries([:]) { ListItem item ->
      ListenerV2 listenerV2 = provider.getListener(region, item.id)
      [(listenerV2.description): listenerV2]
    }
  }

  /**
   * Utility method to get provider
   * @return
   */
  OpenstackClientProvider getProvider() {
    openstackCredentials.provider
  }
}
