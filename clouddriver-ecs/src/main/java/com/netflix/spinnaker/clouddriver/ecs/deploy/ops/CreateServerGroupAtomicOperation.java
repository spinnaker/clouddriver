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
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class CreateServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "CREATE_ECS_SERVER_GROUP";

  private static final String DOCKER_IMAGE = "769716316905.dkr.ecr.us-west-2.amazonaws.com/continuous-delivery:latest";
  private static final String TARGET_GROUP_ARN = "arn:aws:elasticloadbalancing:us-west-2:769716316905:targetgroup/ecs-poc-test/9e8997b7cff00c62";
  private static final String REGION = "us-west-2";
  private static final String CONTAINER_NAME = "springfun-yay";
  private static final String APP_VERSION = "v1337";

  private final CreateServerGroupDescription description;

  @Autowired
  AmazonClientProvider amazonClientProvider;
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    this.description = description;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(BASE_PHASE, "Initializing Create Amazon ECS Server Group Operation...");
    AmazonCredentials credentials = (AmazonCredentials) accountCredentialsProvider.getCredentials(description.getCredentialAccount());
    // TODO: Remove the ternary operator when region is fixed.
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(description.getCredentialAccount(), credentials.getCredentialsProvider(), description.getRegion() != null ? description.getRegion() : REGION);
    String familyName = getFamilyName();
    TaskDefinition taskDefinition = registerTaskDefinition(ecs);

    DeploymentConfiguration deploymentConfiguration = new DeploymentConfiguration();
    deploymentConfiguration.setMinimumHealthyPercent(50);
    deploymentConfiguration.setMaximumPercent(100);

    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(CONTAINER_NAME);
    loadBalancer.setContainerPort(description.getContainerPort());
    String targetGroupARN = TARGET_GROUP_ARN;
    if (description.getTargetGroups() != null && description.getTargetGroups().size() > 0) {
      targetGroupARN = description.getTargetGroups().get(0);
    }
    loadBalancer.setTargetGroupArn(targetGroupARN);

    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(loadBalancer);

    CreateServiceRequest request = new CreateServiceRequest();
    request.setServiceName(familyName + "-" + APP_VERSION);
    request.setDesiredCount(description.getDesiredCount() != null ? description.getDesiredCount() : 0);
    request.setCluster(description.getEcsClusterName());
    request.setRole(description.getIamRole());
    request.setDeploymentConfiguration(deploymentConfiguration);
    request.setLoadBalancers(loadBalancers);
    request.setTaskDefinition(taskDefinition.getTaskDefinitionArn());


    getTask().updateStatus(BASE_PHASE, "Creating " + description.getDesiredCount() + " of " + familyName +
      " with " + taskDefinition.getTaskDefinitionArn() + " for " + description.getCredentialAccount() + ".");
    ecs.createService(request);
    getTask().updateStatus(BASE_PHASE, "Done creating " + description.getDesiredCount() + " of " + familyName +
      " with " + taskDefinition.getTaskDefinitionArn() + " for " + description.getCredentialAccount() + ".");

    return null;
  }

  private TaskDefinition registerTaskDefinition(AmazonECS ecs) {
    KeyValuePair serverGroupEnv = new KeyValuePair();
    serverGroupEnv.setName("SERVER_GROUP");
    serverGroupEnv.setValue(description.getServerGroupVersion());

    KeyValuePair cloudStackEnv = new KeyValuePair();
    cloudStackEnv.setName("CLOUD_STACK");
    cloudStackEnv.setValue(description.getStack());

    KeyValuePair cloudDetailEnv = new KeyValuePair();
    cloudDetailEnv.setName("CLOUD_DETAIL");
    cloudDetailEnv.setValue(description.getDetail());

    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();
    containerEnvironment.add(serverGroupEnv);
    containerEnvironment.add(cloudStackEnv);
    containerEnvironment.add(cloudDetailEnv);

    PortMapping portMapping = new PortMapping();
    portMapping.setHostPort(0);
    portMapping.setContainerPort(description.getContainerPort());
    portMapping.setProtocol(description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

    Collection<PortMapping> portMappings = new LinkedList<>();
    portMappings.add(portMapping);

    ContainerDefinition containerDefinition = new ContainerDefinition();
    containerDefinition.setEnvironment(containerEnvironment);
    containerDefinition.setPortMappings(portMappings);
    containerDefinition.setCpu(description.getComputeUnits());
    containerDefinition.setMemoryReservation(description.getReservedMemory());
    containerDefinition.setImage(DOCKER_IMAGE);
    containerDefinition.setName(CONTAINER_NAME);

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest registerTaskDefinitionRequest = new RegisterTaskDefinitionRequest();
    registerTaskDefinitionRequest.setContainerDefinitions(containerDefinitions);
    registerTaskDefinitionRequest.setFamily(getFamilyName());

    //TODO: add security group to the task def.
    getTask().updateStatus(BASE_PHASE, "Creating Amazon ECS Task Definition...");
    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(registerTaskDefinitionRequest);
    getTask().updateStatus(BASE_PHASE, "Done creating Amazon ECS Task Definition...");

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  private String getFamilyName() {
    String familyName = description.getApplication();

    if (description.getStack() != null) {
      familyName += "-" + description.getStack();
    }
    if (description.getDetail() != null) {
      familyName += "-" + description.getDetail();
    }

    return familyName;
  }
}
