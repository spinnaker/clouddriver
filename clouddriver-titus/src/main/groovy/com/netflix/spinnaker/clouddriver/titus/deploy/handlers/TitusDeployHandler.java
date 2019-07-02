package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import static com.netflix.spinnaker.clouddriver.saga.KatoSagaBridgeDsl.StepBuilder.newStep;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.saga.KatoSagaBridgeDsl;
import com.netflix.spinnaker.clouddriver.saga.SagaProcessor;
import com.netflix.spinnaker.clouddriver.saga.SagaResult;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.config.AwsConfiguration;
import com.netflix.spinnaker.kork.core.RetrySupport;
import groovy.util.logging.Slf4j;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {
  private final TitusClientProvider titusClientProvider;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final Front50Service front50Service;
  private final AwsConfiguration.DeployDefaults deployDefaults;
  private final AwsLookupUtil awsLookupUtil;
  private final SagaProcessor operationSagaProcessor;
  private final ObjectMapper objectMapper;
  private final TargetGroupLookupHelper targetGroupLookupHelper;
  private final RetrySupport retrySupport;

  @Autowired
  public TitusDeployHandler(
      TitusClientProvider titusClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      RegionScopedProviderFactory regionScopedProviderFactory,
      Front50Service front50Service,
      AwsConfiguration.DeployDefaults deployDefaults,
      AwsLookupUtil awsLookupUtil,
      SagaProcessor operationSagaProcessor,
      ObjectMapper objectMapper) {
    this(
        titusClientProvider,
        accountCredentialsProvider,
        accountCredentialsRepository,
        regionScopedProviderFactory,
        front50Service,
        deployDefaults,
        awsLookupUtil,
        operationSagaProcessor,
        objectMapper,
        new TargetGroupLookupHelper(),
        new RetrySupport());
  }

  public TitusDeployHandler(
      TitusClientProvider titusClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      RegionScopedProviderFactory regionScopedProviderFactory,
      Front50Service front50Service,
      AwsConfiguration.DeployDefaults deployDefaults,
      AwsLookupUtil awsLookupUtil,
      SagaProcessor operationSagaProcessor,
      ObjectMapper objectMapper,
      TargetGroupLookupHelper targetGroupLookupHelper,
      RetrySupport retrySupport) {
    this.titusClientProvider = titusClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
    this.front50Service = front50Service;
    this.deployDefaults = deployDefaults;
    this.awsLookupUtil = awsLookupUtil;
    this.operationSagaProcessor = operationSagaProcessor;
    this.objectMapper = objectMapper;
    this.targetGroupLookupHelper = targetGroupLookupHelper;
    this.retrySupport = retrySupport;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public TitusDeploymentResult handle(
      final TitusDeployDescription inputDescription, List priorOutputs) {
    TitusClient titusClient =
        titusClientProvider.getTitusClient(
            inputDescription.getCredentials(), inputDescription.getRegion());

    Saga saga =
        new KatoSagaBridgeDsl()
            .inputs(ImmutableMap.of("description", inputDescription))
            .step(
                newStep("loadFront50App", "loading application attributes")
                    .fn(new LoadFront50AppStep(front50Service, objectMapper))
                    .build())
            .step(
                newStep("prepareDeployment", "preparing deployment")
                    .fn(
                        new PrepareDeploymentStep(
                            accountCredentialsRepository,
                            titusClientProvider,
                            titusClient,
                            awsLookupUtil,
                            deployDefaults,
                            regionScopedProviderFactory,
                            accountCredentialsProvider,
                            targetGroupLookupHelper))
                    .build())
            .step(
                newStep("submitJob", "submitting job request to Titus")
                    .fn(new SubmitJobStep(titusClient, retrySupport))
                    .build())
            .step(
                newStep("copyScalingPolicies", "copy scaling policies")
                    .fn(
                        new CopyScalingPoliciesStep(
                            accountCredentialsRepository, titusClientProvider))
                    .build())
            .step(
                newStep("addLoadBalancers", "adding load balancers")
                    .fn(new AddLoadBalancersStep(titusClientProvider))
                    .build())
            .step(
                newStep("finish", "finishing up")
                    .fn(new FinalizeDeploymentStep(accountCredentialsProvider))
                    .build())
            .build();

    SagaResult<TitusDeploymentResult> result =
        operationSagaProcessor.process(
            saga, (finalState) -> finalState.get("deploymentResult", TitusDeploymentResult.class));

    // TODO(rz): This form of bridging Sagas <-> Tasks isn't very nice. Would be nice for the Saga
    // bridge code to handle this instead.
    saga.getLatestState().getLogs().forEach((it) -> getTask().updateStatus("DEPLOY", it));

    if (result.hasError()) {
      throw new TitusException("An error occurred while applying cloud state", result.getError());
    }

    return result.getResult();
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription;
  }

  public static class Front50Application {
    private String email;
    private boolean platformHealthOnly;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public boolean getPlatformHealthOnly() {
      return platformHealthOnly;
    }

    public void setPlatformHealthOnly(boolean platformHealthOnly) {
      this.platformHealthOnly = platformHealthOnly;
    }
  }
}
