package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.CreateServiceRequest;
import com.amazonaws.services.ecs.model.DeploymentConfiguration;
import com.amazonaws.services.ecs.model.KeyValuePair;
import com.amazonaws.services.ecs.model.LoadBalancer;
import com.amazonaws.services.ecs.model.PortMapping;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CreateServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CREATE_ECS_SERVER_GROUP";

  private final CreateServerGroupDescription description;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  ContainerInformationService containerInformationService;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Create Amazon ECS Server Group Operation...");
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    String versionString = inferServergroupVersion();

    String familyName = getFamilyName();
    String serviceName = familyName + "-" + versionString;

    if (description.getAvailabilityZones().size() != 1) {
      throw new Error(String.format("You must specify exactly 1 region to be used.  You specified %s region(s)"));
    }

    String region = description.getAvailabilityZones().keySet().iterator().next();
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), region);
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, versionString);

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(versionString);
    loadBalancer.setContainerPort(description.getContainerPort());

    if (description.getTargetGroups().size() == 1) {
      AmazonElasticLoadBalancing loadBalancingV2 = amazonClientProvider.getAmazonElasticLoadBalancingV2(description.getCredentialAccount(), credentials.getCredentialsProvider(), region);
      String targetGroupName = description.getTargetGroups().get(0);  // TODO - make target group a single value field in Deck instead of an array here
      DescribeTargetGroupsResult describeTargetGroupsResult = loadBalancingV2.describeTargetGroups(new DescribeTargetGroupsRequest().withNames(targetGroupName));
      loadBalancer.setTargetGroupArn(describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn());
    }

    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(loadBalancer);

    CreateServiceRequest request = new CreateServiceRequest();
    request.setServiceName(serviceName);
    request.setDesiredCount(description.getCapacity().getDesired() != null ? description.getCapacity().getDesired() : 1);
    request.setCluster(description.getEcsClusterName());
    request.setRole(description.getIamRole());
    request.setLoadBalancers(loadBalancers);
    request.setTaskDefinition(taskDefinition.getTaskDefinitionArn());
    request.setDeploymentConfiguration(new DeploymentConfiguration().withMinimumHealthyPercent(50).withMaximumPercent(100));


    getTask().updateStatus(BASE_PHASE, "Creating " + description.getCapacity().getDesired() + " of " + serviceName +
      " with " + taskDefinition.getTaskDefinitionArn() + " for " + description.getCredentialAccount() + ".");
    ecs.createService(request);
    getTask().updateStatus(BASE_PHASE, "Done creating " + description.getCapacity().getDesired() + " of " + serviceName +
      " with " + taskDefinition.getTaskDefinitionArn() + " for " + description.getCredentialAccount() + ".");

    String serverGroupName = region + ":" + serviceName;  // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Arrays.asList(serverGroupName));
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(region, serviceName);

    result.setServerGroupNameByRegion(namesByRegion);

    return result;
  }

  private String inferServergroupVersion() {
    int version = containerInformationService.getLatestServiceVersion(description.getEcsClusterName(), getFamilyName(), description.getCredentialAccount(), description.getRegion());
    return String.format("v%04d", (version+ 1));

  }

  private TaskDefinition registerTaskDefinition(AmazonECS ecs, String versionString) {
    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();
    containerEnvironment.add(new KeyValuePair().withName("SERVER_GROUP").withValue(versionString));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_STACK").withValue(description.getStack()));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_DETAIL").withValue(description.getFreeFormDetails()));

    Collection<PortMapping> portMappings = new LinkedList<>();
    portMappings.add(new PortMapping().withHostPort(0).withContainerPort(description.getContainerPort())
      .withProtocol(description.getPortProtocol() != null ? description.getPortProtocol() : "tcp"));

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(new ContainerDefinition().withName(versionString).withEnvironment(containerEnvironment).withPortMappings(portMappings)
      .withCpu(description.getComputeUnits()).withMemoryReservation(description.getReservedMemory()).withImage(description.getDockerImageAddress()));

    getTask().updateStatus(BASE_PHASE, "Creating Amazon ECS Task Definition...");
    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(new RegisterTaskDefinitionRequest()
      .withContainerDefinitions(containerDefinitions).withFamily(getFamilyName()));
    getTask().updateStatus(BASE_PHASE, "Done creating Amazon ECS Task Definition...");
    return registerTaskDefinitionResult.getTaskDefinition();
  }

  private String getFamilyName() {
    String familyName = description.getApplication();

    if (description.getStack() != null) {
      familyName += "-" + description.getStack();
    }
    if (description.getFreeFormDetails() != null) {
      familyName += "-" + description.getFreeFormDetails();
    }

    return familyName;
  }
}
