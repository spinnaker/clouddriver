/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling
import com.amazonaws.services.applicationautoscaling.model.*
import com.amazonaws.services.ecs.model.*
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.GetRoleResult
import com.amazonaws.services.identitymanagement.model.Role
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector
import com.netflix.spinnaker.clouddriver.model.ServerGroup

import static com.netflix.spinnaker.clouddriver.ecs.deploy.ops.CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_SERVERGROUP

class CreateServerGroupAtomicOperationSpec extends CommonAtomicOperation {
  def iamClient = Mock(AmazonIdentityManagement)
  def iamPolicyReader = Mock(IamPolicyReader)
  def loadBalancingV2 = Mock(AmazonElasticLoadBalancing)
  def autoScalingClient = Mock(AWSApplicationAutoScaling)
  def subnetSelector = Mock(SubnetSelector)
  def securityGroupSelector = Mock(SecurityGroupSelector)
  def objectMapper = Mock(ObjectMapper)
  def artifactDownloader = Mock(ArtifactDownloader)

  def applicationName = 'myapp'
  def stack = 'kcats'
  def detail = 'liated'
  def serviceName = "${applicationName}-${stack}-${detail}"

  def trustRelationships = [new IamTrustRelationship(type: 'Service', value: 'ecs-tasks.amazonaws.com'),
                            new IamTrustRelationship(type: 'Service', value: 'ecs.amazonaws.com')]

  def role = new Role(assumeRolePolicyDocument: "json-encoded-string-here")

  def creds = Mock(NetflixAssumeRoleAmazonCredentials) {
    getName() >> { "test" }
    getRegions() >> { [new AmazonCredentials.AWSRegion('us-west-1', ['us-west-1a', 'us-west-1b'])] }
    getAssumeRole() >> { 'test-role' }
    getAccountId() >> { 'test' }
  }

  def taskDefinition = new TaskDefinition().withTaskDefinitionArn("task-def-arn")

  def targetGroup = new TargetGroup().withLoadBalancerArns("loadbalancer-arn").withTargetGroupArn('target-group-arn')

  def service = new Service(serviceName: "${serviceName}-v008")

  def 'should create a service'() {
    given:
    def source = new CreateServerGroupDescription.Source()
    source.account = "test"
    source.region = "us-west-1"
    source.asgName = "${serviceName}-v007"
    source.useSourceCapacity = true

    def placementConstraint = new PlacementConstraint(type: 'memberOf', expression: 'attribute:ecs.instance-type =~ t2.*')

    def placementStrategy = new PlacementStrategy(type: 'spread', field: 'attribute:ecs.availability-zone')

    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      tags: ['label1': 'value1', 'fruit': 'tomato'],
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [placementStrategy],
      placementConstraints: [placementConstraint],
      source: source
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonIdentityManagement(_, _, _) >> iamClient
    amazonClientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> loadBalancingV2
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoScalingClient
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    accountCredentialsProvider.getCredentials(_) >> creds

    when:
    def result = operation.operate([])

    then:
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices(_) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date(), desiredCount: 3))

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)
    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)
    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries == []
      request.desiredCount == 3
      request.role == 'arn:aws:iam::test:test-role'
      request.placementConstraints.size() == 1
      request.placementConstraints.get(0).type == 'memberOf'
      request.placementConstraints.get(0).expression == 'attribute:ecs.instance-type =~ t2.*'
      request.placementStrategy.size() == 1
      request.placementStrategy.get(0).type == 'spread'
      request.placementStrategy.get(0).field == 'attribute:ecs.availability-zone'
      request.networkConfiguration == null
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == true
      request.propagateTags == 'SERVICE'
      request.tags.size() == 2
      request.tags.get(0).key == 'label1'
      request.tags.get(0).value == 'value1'
      request.tags.get(1).key == 'fruit'
      request.tags.get(1).value == 'tomato'
      request.launchType == null
      request.platformVersion == null
    }) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")

    1 * autoScalingClient.registerScalableTarget(_) >> { arguments ->
      RegisterScalableTargetRequest request = arguments.get(0)
      assert request.serviceNamespace == ServiceNamespace.Ecs.toString()
      assert request.scalableDimension == ScalableDimension.EcsServiceDesiredCount.toString()
      assert request.resourceId == "service/test-cluster/${serviceName}-v008"
      assert request.roleARN == 'arn:aws:iam::test:test-role'
      assert request.minCapacity == 2
      assert request.maxCapacity == 4
    }

    autoScalingClient.describeScalableTargets(_) >> new DescribeScalableTargetsResult()
      .withScalableTargets(new ScalableTarget()
      .withResourceId("service/test-cluster/${serviceName}-v007")
      .withMinCapacity(2)
      .withMaxCapacity(4))

    1 * operation.ecsCloudMetricService.copyScalingPolicies(
      "Test",
      "us-west-1",
      "${serviceName}-v008",
      "service/test-cluster/${serviceName}-v008",
      "test",
      "us-west-1",
      "${serviceName}-v007",
      "service/test-cluster/${serviceName}-v007",
      "test-cluster"
    )
  }

  def 'should create a service using VPC and Fargate mode'() {
    given:
    def serviceRegistry = new CreateServerGroupDescription.ServiceDiscoveryAssociation(
      registry: new CreateServerGroupDescription.ServiceRegistry(arn: 'srv-registry-arn'),
      containerPort: 9090
    )
    def description = new CreateServerGroupDescription(
      credentials: TestCredential.named('Test', [:]),
      application: applicationName,
      stack: stack,
      freeFormDetails: detail,
      ecsClusterName: 'test-cluster',
      iamRole: 'test-role',
      containerPort: 1337,
      targetGroup: 'target-group-arn',
      portProtocol: 'tcp',
      computeUnits: 9001,
      reservedMemory: 9002,
      dockerImageAddress: 'docker-image-url',
      capacity: new ServerGroup.Capacity(1, 1, 1),
      availabilityZones: ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']],
      placementStrategySequence: [],
      launchType: 'FARGATE',
      platformVersion: '1.0.0',
      networkMode: 'awsvpc',
      subnetType: 'public',
      securityGroupNames: ['helloworld'],
      associatePublicIpAddress: true,
      serviceDiscoveryAssociations: [serviceRegistry]
    )

    def operation = new CreateServerGroupAtomicOperation(description)

    operation.amazonClientProvider = amazonClientProvider
    operation.ecsCloudMetricService = Mock(EcsCloudMetricService)
    operation.iamPolicyReader = iamPolicyReader
    operation.accountCredentialsProvider = accountCredentialsProvider
    operation.containerInformationService = containerInformationService
    operation.subnetSelector = subnetSelector
    operation.securityGroupSelector = securityGroupSelector

    amazonClientProvider.getAmazonEcs(_, _, _) >> ecs
    amazonClientProvider.getAmazonIdentityManagement(_, _, _) >> iamClient
    amazonClientProvider.getAmazonElasticLoadBalancingV2(_, _, _) >> loadBalancingV2
    amazonClientProvider.getAmazonApplicationAutoScaling(_, _, _) >> autoScalingClient
    containerInformationService.getClusterName(_, _, _) >> 'cluster-name'
    accountCredentialsProvider.getCredentials(_) >> creds

    subnetSelector.resolveSubnetsIds(_, _, _) >> ['subnet-12345']
    subnetSelector.getSubnetVpcIds(_, _, _) >> ['vpc-123']
    securityGroupSelector.resolveSecurityGroupNames(_, _, _, _) >> ['sg-12345']

    when:
    def result = operation.operate([])

    then:
    ecs.listServices(_) >> new ListServicesResult().withServiceArns("${serviceName}-v007")
    ecs.describeServices(_) >> new DescribeServicesResult().withServices(
      new Service(serviceName: "${serviceName}-v007", createdAt: new Date()))

    ecs.registerTaskDefinition(_) >> new RegisterTaskDefinitionResult().withTaskDefinition(taskDefinition)

    iamClient.getRole(_) >> new GetRoleResult().withRole(role)
    iamPolicyReader.getTrustedEntities(_) >> trustRelationships
    loadBalancingV2.describeTargetGroups(_) >> new DescribeTargetGroupsResult().withTargetGroups(targetGroup)

    ecs.createService({ CreateServiceRequest request ->
      request.cluster == 'test-cluster'
      request.serviceName == 'myapp-kcats-liated-v008'
      request.taskDefinition == 'task-def-arn'
      request.loadBalancers.size() == 1
      request.loadBalancers.get(0).targetGroupArn == 'target-group-arn'
      request.loadBalancers.get(0).containerName == 'v008'
      request.loadBalancers.get(0).containerPort == 1337
      request.serviceRegistries.size() == 1
      request.serviceRegistries.get(0) == new ServiceRegistry(
        registryArn: 'srv-registry-arn',
        containerPort: 9090,
        containerName: 'v008'
      )
      request.desiredCount == 1
      request.role == null
      request.placementStrategy == []
      request.placementConstraints == []
      request.networkConfiguration.awsvpcConfiguration.subnets == ['subnet-12345']
      request.networkConfiguration.awsvpcConfiguration.securityGroups == ['sg-12345']
      request.networkConfiguration.awsvpcConfiguration.assignPublicIp == 'ENABLED'
      request.healthCheckGracePeriodSeconds == null
      request.enableECSManagedTags == null
      request.propagateTags == null
      request.tags == []
      request.launchType == 'FARGATE'
      request.platformVersion == '1.0.0'
    } as CreateServiceRequest) >> new CreateServiceResult().withService(service)

    result.getServerGroupNames().size() == 1
    result.getServerGroupNameByRegion().size() == 1
    result.getServerGroupNames().contains("us-west-1:" + serviceName + "-v008")
    result.getServerGroupNameByRegion().containsKey('us-west-1')
    result.getServerGroupNameByRegion().get('us-west-1').contains(serviceName + "-v008")
  }

  def 'should create services without load balancers'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getTargetGroup() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeServiceRequest('task-def-arn',
      'arn:aws:iam::test:test-role',
      'mygreatapp-stack1-details2-v0011',
      1)

    then:
    request.getLoadBalancers() == []
    request.getRole() == null
  }

  def 'should create default Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> null

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v0011'
    labels.get(CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_STACK) == 'stack1'
    labels.get(CreateServerGroupAtomicOperation.DOCKER_LABEL_KEY_DETAIL) == 'details2'
  }

  def 'should create custom Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> ['label1': 'value1', 'fruit':'tomato']

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get('label1') == 'value1'
    labels.get('fruit') == 'tomato'
  }

  def 'should not allow overwriting Spinnaker Docker labels'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    def dockerLabels = [:]
    dockerLabels.put(DOCKER_LABEL_KEY_SERVERGROUP, 'some-value-we-dont-want-to-see')

    description.getApplication() >> 'mygreatapp'
    description.getStack() >> 'stack1'
    description.getFreeFormDetails() >> 'details2'
    description.getDockerLabels() >> dockerLabels

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    def labels = request.getContainerDefinitions().get(0).getDockerLabels()
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) == 'mygreatapp-stack1-details2-v0011'
    labels.get(DOCKER_LABEL_KEY_SERVERGROUP) != 'some-value-we-dont-want-to-see'
  }


  def 'should allow selecting the logDriver'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getLogDriver() == 'some-log-driver'
  }

  def 'should allow empty logOptions'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getOptions() == null
  }

  def 'should allow registering logOptions'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getLogDriver() >> 'some-log-driver'
    def logOptions = ['key1': '1value', 'key2': 'value2']
    description.getLogOptions() >> logOptions

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getLogConfiguration().getOptions() == logOptions
  }

  def 'should allow no port mappings'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getContainerPort() >> null
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getPortMappings().isEmpty()
  }

  def 'should allow using secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getDockerImageCredentialsSecret() >> 'my-secret'

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials().getCredentialsParameter() == 'my-secret'
  }

  def 'should allow not specifying secret credentials for the docker image'() {
    given:
    def description = Mock(CreateServerGroupDescription)

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    request.getContainerDefinitions().get(0).getRepositoryCredentials() == null
  }

  def 'should generate a RegisterTaskDefinitionRequest object'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'kcats'
    description.getFreeFormDetails() >> 'liated'
    description.ecsClusterName = 'test-cluster'
    description.iamRole = 'None (No IAM role)'
    description.getContainerPort() >> 1337
    description.targetGroup = 'target-group-arn'
    description.getPortProtocol() >> 'tcp'
    description.getComputeUnits() >> 9001
    description.getReservedMemory() >> 9001
    description.getDockerImageAddress() >> 'docker-image-url'
    description.capacity = new ServerGroup.Capacity(1, 1, 1)
    description.availabilityZones = ['us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c']]
    description.placementStrategySequence = []

    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", "v1-kcats-liated-v001")

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-kcats-liated"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == 'v001'
    containerDefinition.image == 'docker-image-url'
    containerDefinition.cpu == 9001
    containerDefinition.memoryReservation == 9001

    containerDefinition.portMappings.size() == 1
    def portMapping = containerDefinition.portMappings.first()
    portMapping.getHostPort() == 0
    portMapping.getContainerPort() == 1337
    portMapping.getProtocol() == 'tcp'

    containerDefinition.environment.size() == 3
    def environments = [:]
    for(elem in containerDefinition.environment){
      environments.put(elem.getName(), elem.getValue())
    }
    environments.get("SERVER_GROUP") == "v1-kcats-liated-v001"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
  }

  def 'should generate a RegisterTaskDefinitionRequest object from artifact'() {
    given:
    def resolvedArtifact = [
      name: "taskdef.json",
      reference: "fake.github.com/repos/org/repo/taskdef.json",
      artifactAccount: "my-github-acct",
      type: "github/file"
    ]
    def containerDef1 =
      new ContainerDefinition()
        .withName("web")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(512)
    def containerDef2 =
      new ContainerDefinition()
        .withName("logs")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(1024)
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest()
        .withContainerDefinitions([containerDef1, containerDef2])
        .withExecutionRoleArn("arn:aws:role/myExecutionRole")
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.ecsClusterName = 'test-cluster'
    description.iamRole = 'None (No IAM role)'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url/one",
      logs: "docker-image-url/two"
    ]

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact("test-role", "v1-ecs-test-v001")

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "arn:aws:role/myExecutionRole"

    result.getContainerDefinitions().size() == 2

    def webContainer = result.getContainerDefinitions().find {it.getName() == "web"}
    assert webContainer != null
    webContainer.image == "docker-image-url/one"
    webContainer.memoryReservation == 512

    def logsContainer = result.getContainerDefinitions().find {it.getName() == "logs"}
    assert logsContainer != null
    logsContainer.image == "docker-image-url/two"
    logsContainer.memoryReservation == 1024

    result.getContainerDefinitions().forEach({
      it.environment.size() == 3

      def environments = [:]
      for(elem in it.environment){
        environments.put(elem.getName(), elem.getValue())
      }
      environments.get("SERVER_GROUP") == "v1-ecs-test-v001"
      environments.get("CLOUD_STACK") == "ecs"
      environments.get("CLOUD_DETAIL") == "test"
    })
  }

  def 'should set spinnaker role on FARGATE RegisterTaskDefinitionRequest if none in artifact'() {
    given:
    def resolvedArtifact = [
      name: "taskdef.json",
      reference: "fake.github.com/repos/org/repo/taskdef.json",
      artifactAccount: "my-github-acct",
      type: "github/file"
    ]
    def containerDef =
      new ContainerDefinition()
        .withName("web")
        .withImage("PLACEHOLDER")
        .withMemoryReservation(512)
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest().withContainerDefinitions([containerDef])
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.ecsClusterName = 'test-cluster'
    description.iamRole = 'None (No IAM role)'
    description.getLaunchType() >> 'FARGATE'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact
    description.getContainerToImageMap() >> [
      web: "docker-image-url"
    ]

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    RegisterTaskDefinitionRequest result =
      operation.makeTaskDefinitionRequestFromArtifact("test-role", "v1-ecs-test-v001")

    then:
    result.getTaskRoleArn() == null
    result.getFamily() == "v1-ecs-test"
    result.getExecutionRoleArn() == "test-role"

    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.name == "web"
    containerDefinition.image == "docker-image-url"
    containerDefinition.memoryReservation == 512
  }

  def 'should fail if network mode in artifact does not match description'() {
    given:
    def resolvedArtifact = [
      name: "taskdef.json",
      reference: "fake.github.com/repos/org/repo/taskdef.json",
      artifactAccount: "my-github-acct",
      type: "github/file"
    ]
    def registerTaskDefRequest =
      new RegisterTaskDefinitionRequest()
        .withContainerDefinitions([new ContainerDefinition()])
        .withNetworkMode("bridge")
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'ecs'
    description.getFreeFormDetails() >> 'test'
    description.ecsClusterName = 'test-cluster'
    description.getLaunchType() >> 'FARGATE'
    description.getNetworkMode() >> 'awsvpc'
    description.getResolvedTaskDefinitionArtifact() >> resolvedArtifact

    def operation = new CreateServerGroupAtomicOperation(description)
    operation.artifactDownloader = artifactDownloader
    operation.mapper = objectMapper

    artifactDownloader.download(_) >> new ByteArrayInputStream()
    objectMapper.readValue(_,_) >> registerTaskDefRequest

    when:
    operation.makeTaskDefinitionRequestFromArtifact("test-role", "v1-ecs-test-v001")

    then:
    IllegalArgumentException exception = thrown()
    exception.message ==
      "Task definition networkMode does not match server group value. Found 'bridge' but expected 'awsvpc'"
  }

  def 'should set additional environment variables'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getApplication() >> 'v1'
    description.getStack() >> 'kcats'
    description.getFreeFormDetails() >> 'liated'
    description.getEnvironmentVariables() >> ["ENVIRONMENT_1" : "test1", "ENVIRONMENT_2" : "test2"]
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    RegisterTaskDefinitionRequest result = operation.makeTaskDefinitionRequest("test-role", "v1-kcats-liated-v001")

    then:
    result.getContainerDefinitions().size() == 1
    def containerDefinition = result.getContainerDefinitions().first()
    containerDefinition.environment.size() == 5
    def environments = [:]
    for(elem in containerDefinition.environment){
      environments.put(elem.getName(), elem.getValue())
    }
    environments.get("SERVER_GROUP") == "v1-kcats-liated-v001"
    environments.get("CLOUD_STACK") == "kcats"
    environments.get("CLOUD_DETAIL") == "liated"
    environments.get("ENVIRONMENT_1") == "test1"
    environments.get("ENVIRONMENT_2") == "test2"
  }

  def 'should use same port for host and container in host mode'() {
    given:
    def description = Mock(CreateServerGroupDescription)
    description.getContainerPort() >> 10000
    description.getNetworkMode() >> 'host'
    def operation = new CreateServerGroupAtomicOperation(description)

    when:
    def request = operation.makeTaskDefinitionRequest('arn:aws:iam::test:test-role', 'mygreatapp-stack1-details2-v0011')

    then:
    def portMapping = request.getContainerDefinitions().get(0).getPortMappings().get(0)
    portMapping.getHostPort() == 10000
    portMapping.getContainerPort() == 10000
    portMapping.getProtocol() == 'tcp'
  }
}
