/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.OpenstackServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import org.apache.commons.io.IOUtils
import org.openstack4j.model.network.ext.LbPool

/**
 * For now, we want to provide 'the standard' way of being able to configure an autoscaling group in much the same way
 * as it is done with other providers, albeit with the hardcoded templates.
 * Later on we should consider adding in the feature to provide custom templates.
 *
 * Overriding the default via configuration is a good idea, as long as people do their diligence to honor
 * the properties that the template can expect to be given to it. The Openstack API is finicky when properties
 * are provided but not used, and doesn't work at all when properties are not provided but expected.
 *
 * Being able to pass in the template via free-form text is also a good idea,
 * but again it would need to honor the expected parameters.
 * We could use the freeform details field to store the template string.
 */
class DeployOpenstackAtomicOperation implements AtomicOperation<DeploymentResult> {

  private final String BASE_PHASE = "DEPLOY"

  //this is the file name of the heat template used to create the auto scaling group,
  //and needs to be loaded into memory as a String
  private final String TEMPLATE_FILE = 'asg.yaml'

  //this is the name of the subtemplate referenced by the template,
  //and needs to be loaded into memory as a String
  private final String SUBTEMPLATE_FILE = 'asg_resource.yaml'

  DeployOpenstackAtomicOperationDescription description;

  DeployOpenstackAtomicOperation(DeployOpenstackAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
  * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "stack": "teststack", "application": "myapp", "region": "RegionOne", "heatTemplate": "{\"heat_template_version\":\"2013-05-23\",\"description\":\"Simple template to test heat commands\",\"parameters\":{\"flavor\":{\"default\":\"m1.nano\",\"type\":\"string\"}},\"resources\":{\"hello_world\":{\"type\":\"OS::Nova::Server\",\"properties\":{\"flavor\":{\"get_param\":\"flavor\"},\"image\":\"cirros-0.3.4-x86_64-uec\",\"user_data\":\"\"}}}}", "timeoutMins": "5", "account": "test" }} ]' localhost:7002/openstack/ops
  * curl -X GET -H "Accept: application/json" localhost:7002/task/1
  */
  @Override
  DeploymentResult operate(List priorOutputs) {
    DeploymentResult deploymentResult = new DeploymentResult()
    try {
      task.updateStatus BASE_PHASE, "Initializing creation of server group"
      OpenstackClientProvider provider = description.credentials.provider

      def serverGroupNameResolver = new OpenstackServerGroupNameResolver(description.credentials, description.region)
      def groupName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

      task.updateStatus BASE_PHASE, "Looking up next sequence index for cluster ${groupName}..."
      def stackName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
      task.updateStatus BASE_PHASE, "Heat stack name chosen to be ${stackName}."

      task.updateStatus BASE_PHASE, "Loading templates"
      String template = IOUtils.toString(this.class.classLoader.getResourceAsStream(TEMPLATE_FILE))
      String subtemplate = IOUtils.toString(this.class.classLoader.getResourceAsStream(SUBTEMPLATE_FILE))
      task.updateStatus BASE_PHASE, "Finished loading templates"

      task.updateStatus BASE_PHASE, "Getting load balancer details for pool id $description.serverGroupParameters.poolId"
      LbPool pool = provider.getLoadBalancerPool(description.region, description.serverGroupParameters.poolId)
      task.updateStatus BASE_PHASE, "Found load balancer details for pool id $description.serverGroupParameters.poolId with name $pool.name"

      task.updateStatus BASE_PHASE, "Getting internal port used for load balancer $pool.name"
      int port = provider.getInternalLoadBalancerPort(pool)
      task.updateStatus BASE_PHASE, "Found internal port $port used for load balancer $pool.name"

      task.updateStatus BASE_PHASE, "Creating heat stack $stackName"
      provider.deploy(description.region, stackName, template, [(SUBTEMPLATE_FILE): subtemplate], description.serverGroupParameters.identity {
        internalPort = port; it
      }, description.disableRollback, description.timeoutMins)
      task.updateStatus BASE_PHASE, "Finished creating heat stack $stackName"

      task.updateStatus BASE_PHASE, "Successfully created server group."

      deploymentResult.deployedNames = Arrays.asList(stackName)
      deploymentResult.deployedNamesByLocation = [(description.region): deploymentResult.deployedNames]
    } catch (Exception e) {
      throw new OpenstackOperationException(AtomicOperations.CREATE_SERVER_GROUP, e)
    }
    deploymentResult

  }
}
