/*
 * Copyright 2016 Target, Inc.
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
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackProviderFactory
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ResizeOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackOperationException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.domain.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import org.openstack4j.model.heat.Stack
import spock.lang.Specification
import spock.lang.Subject

class ResizeOpenstackAtomicOperationSpec extends Specification {
  String accountName = 'myaccount'
  String application = "app"
  String stack = "stack"
  String region = "r1"
  int maxSize = 5
  int minSize = 3
  String createdStackName = 'app-stack-details-v000'
  String stackId = UUID.randomUUID().toString()


  def credentials
  def serverGroupParams
  def description
  def provider

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  def setup() {
    provider = Mock(OpenstackClientProvider)
    GroovyMock(OpenstackProviderFactory, global : true)
    OpenstackNamedAccountCredentials creds = Mock(OpenstackNamedAccountCredentials)
    OpenstackProviderFactory.createProvider(creds) >> { provider }
    credentials = new OpenstackCredentials(creds)
    serverGroupParams = new ServerGroupParameters(maxSize: maxSize, minSize: minSize)
    description = new ResizeOpenstackAtomicOperationDescription(region: region, account: accountName, credentials: credentials, serverGroupName: createdStackName, capacity: new ResizeOpenstackAtomicOperationDescription.Capacity(min: 3, max: 5))
  }

  def "should resize a heat stack"() {
    given:
    @Subject def operation = new ResizeOpenstackAtomicOperation(description)
    Stack stack = Mock(Stack)
    String template = "foo: bar"
    Map<String, String> sub = ['asg_resource.yaml':'foo: bar']

    when:
    operation.operate([])

    then:
    1 * provider.getStack(region, createdStackName) >> stack
    1 * provider.getHeatTemplate(region, createdStackName, stackId) >> template
    1 * stack.getOutputs() >> [[output_key:operation.SUBTEMPLATE_OUTPUT, output_value: sub['asg_resource.yaml']]]
    1 * stack.getParameters() >> serverGroupParams.toParamsMap()
    _ * stack.getId() >> stackId
    _ * stack.getName() >> createdStackName
    1 * provider.updateStack(region, createdStackName, stackId, template, sub, serverGroupParams)
    noExceptionThrown()
  }

  def "should not resize a heat stack if the stack is missing"() {
    given:
    @Subject def operation = new ResizeOpenstackAtomicOperation(description)

    when:
    operation.operate([])

    then:
    1 * provider.getStack(region, createdStackName) >> null
    thrown(OpenstackOperationException)
  }

  def "should not resize a stack if exception is thrown"() {
    given:
    @Subject def operation = new ResizeOpenstackAtomicOperation(description)
    Stack stack = Mock(Stack)
    String template = "foo: bar"
    Map<String, String> sub = ['asg_resource.yaml':'foo: bar']

    when:
    operation.operate([])

    then:
    1 * provider.getStack(region, createdStackName) >> stack
    1 * provider.getHeatTemplate(region, createdStackName, stackId) >> template
    1 * stack.getOutputs() >> [[output_key:operation.SUBTEMPLATE_OUTPUT, output_value: sub['asg_resource.yaml']]]
    1 * stack.getParameters() >> serverGroupParams.toParamsMap()
    _ * stack.getId() >> stackId
    _ * stack.getName() >> createdStackName
    1 * provider.updateStack(region, createdStackName, stackId, template, sub, serverGroupParams) >> { throw new OpenstackProviderException('foo') }
    thrown(OpenstackOperationException)
  }
}
