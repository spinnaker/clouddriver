/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.cloudformation.AmazonCloudFormation
import com.amazonaws.services.cloudformation.model.ChangeSetType
import com.amazonaws.services.cloudformation.model.CreateChangeSetRequest
import com.amazonaws.services.cloudformation.model.CreateChangeSetResult
import com.amazonaws.services.cloudformation.model.CreateStackRequest
import com.amazonaws.services.cloudformation.model.CreateStackResult
import com.amazonaws.services.cloudformation.model.DescribeStacksResult
import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Stack
import com.amazonaws.services.cloudformation.model.Tag
import com.amazonaws.services.cloudformation.model.UpdateStackRequest
import com.amazonaws.services.cloudformation.model.UpdateStackResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeployCloudFormationDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Unroll

class DeployCloudFormationAtomicOperationSpec extends Specification {
  void setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "should build a CreateStackRequest if stack doesn't exist and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def createStackResult = Mock(CreateStackResult)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: [ key: "value" ],
          parameters: [ key: "value"],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> { throw new IllegalArgumentException() }
    1 * amazonCloudFormation.createStack(_) >> { CreateStackRequest request ->
      assert request.getStackName() == "stackTest"
      assert request.getTemplateBody() == '{"key":"value"}'
      assert request.getParameters() == [ new Parameter().withParameterKey("key").withParameterValue("value") ]
      assert request.getTags() == [ new Tag().withKey("key").withValue("value") ]
      assert request.getCapabilities() == ["cap1", "cap2"]
      createStackResult
    }
    1 * createStackResult.getStackId() >> stackId
  }

  void "should build an UpdateStackRequest if stack exists and submit through aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def updateStackRequest = Mock(UpdateStackResult)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: [ key: "value" ],
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test")
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> {
      new DescribeStacksResult().withStacks([new Stack().withStackId("stackId")] as Collection)
    }
    1 * amazonCloudFormation.updateStack(_) >> { UpdateStackRequest request ->
      assert request.getStackName() == "stackTest"
      assert request.getTemplateBody() == '{"key":"value"}'
      assert request.getParameters() == [ new Parameter().withParameterKey("key").withParameterValue("value") ]
      assert request.getTags() == [ new Tag().withKey("key").withValue("value") ]
      assert request.getCapabilities() == ["cap1", "cap2"]
      updateStackRequest
    }
    1 * updateStackRequest.getStackId() >> stackId
  }

  @Unroll
  void "should build an CreateChangeSetRequest if it's a changeset and submit though aws client"() {
    given:
    def amazonClientProvider = Mock(AmazonClientProvider)
    def amazonCloudFormation = Mock(AmazonCloudFormation)
    def createChangeSetResult = Mock(CreateChangeSetResult)
    def stackId = "stackId"
    def op = new DeployCloudFormationAtomicOperation(
      new DeployCloudFormationDescription(
        [
          stackName: "stackTest",
          region: "eu-west-1",
          templateBody: [ key: "value" ],
          parameters: [ key: "value" ],
          tags: [ key: "value" ],
          capabilities: ["cap1", "cap2"],
          credentials: TestCredential.named("test"),
          isChangeSet: true,
          changeSetName: "changeSetTest"
        ]
      )
    )
    op.amazonClientProvider = amazonClientProvider
    op.objectMapper = new ObjectMapper()

    when:
    op.operate([])

    then:
    1 * amazonClientProvider.getAmazonCloudFormation(_, _) >> amazonCloudFormation
    1 * amazonCloudFormation.describeStacks(_) >> {
      if (existingStack) {
        new DescribeStacksResult().withStacks([new Stack().withStackId("stackId")] as Collection)
      } else {
        new DescribeStacksResult().withStacks([] as Collection)
      }
    }
    1* amazonCloudFormation.createChangeSet(_) >> { CreateChangeSetRequest request ->
      assert request.getStackName() == "stackTest"
      assert request.getTemplateBody() == '{"key":"value"}'
      assert request.getParameters() == [ new Parameter().withParameterKey("key").withParameterValue("value") ]
      assert request.getTags() == [ new Tag().withKey("key").withValue("value") ]
      assert request.getCapabilities() == ["cap1", "cap2"]
      assert request.getChangeSetName() == "changeSetTest"
      assert request.getChangeSetType() == changeSetType
      createChangeSetResult
    }
    1 * createChangeSetResult.getStackId() >> stackId

    where:
    existingStack || changeSetType
    true          || ChangeSetType.UPDATE.toString()
    false         || ChangeSetType.CREATE.toString()
  }
}
