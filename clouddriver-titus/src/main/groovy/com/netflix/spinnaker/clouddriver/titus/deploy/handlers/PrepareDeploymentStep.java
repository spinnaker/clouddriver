/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import static java.lang.String.format;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.saga.DefaultStepResult;
import com.netflix.spinnaker.clouddriver.saga.SagaStepFunction;
import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.DisruptionBudget;
import com.netflix.spinnaker.clouddriver.titus.client.model.Job;
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.AvailabilityPercentageLimit;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.ContainerHealthProvider;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.HourlyTimeWindow;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.RatePercentagePerInterval;
import com.netflix.spinnaker.clouddriver.titus.client.model.disruption.TimeWindow;
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.exceptions.IllegalOperationStateException;
import com.netflix.spinnaker.clouddriver.titus.exceptions.JobNotFoundException;
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage;
import com.netflix.spinnaker.config.AwsConfiguration;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepares a final TitusDeployDescription for the TitusDeployHandler.
 *
 * <p>TODO(rz): simplify interactions with lists / maps. Refactored the description to initialize
 * these all the time. TODO(rz): Create a front50Application model. Working with maps sucks.
 */
public class PrepareDeploymentStep extends AbstractTitusDeployStep implements SagaStepFunction {

  private static final Logger log = LoggerFactory.getLogger(PrepareDeploymentStep.class);

  private static final TimeWindow DEFAULT_SYSTEM_TIME_WINDOW =
      new TimeWindow(
          Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
          Collections.singletonList(new HourlyTimeWindow(10, 16)),
          "PST");
  private static final String USE_APPLICATION_DEFAULT_SG_LABEL =
      "spinnaker.useApplicationDefaultSecurityGroup";
  private static final String LABEL_TARGET_GROUPS = "spinnaker.targetGroups";

  private final AwsLookupUtil awsLookupUtil;
  private final AwsConfiguration.DeployDefaults deployDefaults;
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final TargetGroupLookupHelper targetGroupLookupHelper;

  public PrepareDeploymentStep(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider,
      AwsLookupUtil awsLookupUtil,
      AwsConfiguration.DeployDefaults deployDefaults,
      RegionScopedProviderFactory regionScopedProviderFactory,
      AccountCredentialsProvider accountCredentialsProvider,
      TargetGroupLookupHelper targetGroupLookupHelper) {
    super(accountCredentialsRepository, titusClientProvider);
    this.awsLookupUtil = awsLookupUtil;
    this.deployDefaults = deployDefaults;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.targetGroupLookupHelper = targetGroupLookupHelper;
  }

  private static <T> T orDefault(T input, T defaultValue) {
    return (input == null) ? defaultValue : input;
  }

  private static int orDefault(int input, int defaultValue) {
    if (input == 0) {
      return defaultValue;
    }
    return input;
  }

  @Override
  public StepResult apply(SagaState state) {
    final TitusDeployDescription description =
        state.get("description", TitusDeployDescription.class);
    if (description == null) {
      // TODO(rz): Update kork-exceptions to make additional methods use a fluent interface
      IntegrationException e =
          new IllegalOperationStateException(
              "Expected a 'description' state object, but none was defined");
      e.setRetryable(false);
      throw e;
    }

    final TitusDeployHandler.Front50Application front50Application =
        state.get("front50Application");

    final String asgName = description.getSource().getAsgName();
    if (!isNullOrEmpty(asgName)) {
      log.trace("Source present, getting details: {}", asgName);
      Names sourceName = Names.parseName(asgName);
      description.setApplication(
          description.getApplication() != null
              ? description.getApplication()
              : sourceName.getApp());
      description.setStack(
          description.getStack() != null ? description.getStack() : sourceName.getStack());
      description.setFreeFormDetails(
          description.getFreeFormDetails() != null
              ? description.getFreeFormDetails()
              : sourceName.getDetail());

      TitusDeployDescription.Source source = description.getSource();

      TitusClient sourceClient = buildSourceTitusClient(source);
      if (sourceClient == null) {
        // TODO(rz): Specific exception.
        throw new IntegrationException(
            "Unable to find a Titus client for deployment source: {}", asgName);
      }

      Job sourceJob = sourceClient.findJobByName(source.getAsgName());
      if (sourceJob == null) {
        throw new JobNotFoundException(
            format(
                "Unable to locate source (%s:%s:%s)",
                source.getAccount(), source.getRegion(), source.getAsgName()));
      }

      state.appendLog(
          format(
              "Copying deployment details from (%s:%s:%s)",
              source.getAccount(), source.getRegion(), source.getAsgName()));

      if (isNullOrEmpty(description.getSecurityGroups())) {
        description.setSecurityGroups(sourceJob.getSecurityGroups());
      }
      if (isNullOrEmpty(description.getImageId())) {
        String imageVersion =
            (sourceJob.getVersion() == null) ? sourceJob.getDigest() : sourceJob.getVersion();
        description.setImageId(format("%s:%s", sourceJob.getApplicationName(), imageVersion));
      }

      if (description.getSource().getUseSourceCapacity()) {
        description.getCapacity().setMin(sourceJob.getInstancesMin());
        description.getCapacity().setMax(sourceJob.getInstancesMax());
        description.getCapacity().setDesired(sourceJob.getInstancesDesired());
      }

      description
          .getResources()
          .setAllocateIpAddress(
              orDefault(
                  description.getResources().isAllocateIpAddress(),
                  sourceJob.isAllocateIpAddress()));
      description
          .getResources()
          .setCpu(orDefault(description.getResources().getCpu(), sourceJob.getCpu()));
      description
          .getResources()
          .setDisk(orDefault(description.getResources().getDisk(), sourceJob.getDisk()));
      description
          .getResources()
          .setGpu(orDefault(description.getResources().getGpu(), sourceJob.getGpu()));
      description
          .getResources()
          .setMemory(orDefault(description.getResources().getMemory(), sourceJob.getMemory()));
      description
          .getResources()
          .setNetworkMbps(
              orDefault(description.getResources().getNetworkMbps(), sourceJob.getNetworkMbps()));
      description.setRetries(orDefault(description.getRetries(), sourceJob.getRetries()));
      description.setRuntimeLimitSecs(
          orDefault(description.getRuntimeLimitSecs(), sourceJob.getRuntimeLimitSecs()));
      description.setEfs(orDefault(description.getEfs(), sourceJob.getEfs()));
      description.setEntryPoint(orDefault(description.getEntryPoint(), sourceJob.getEntryPoint()));
      description.setIamProfile(orDefault(description.getIamProfile(), sourceJob.getIamProfile()));
      description.setCapacityGroup(
          orDefault(description.getCapacityGroup(), sourceJob.getCapacityGroup()));
      description.setInService(orDefault(description.getInService(), sourceJob.isInService()));
      description.setJobType(orDefault(description.getJobType(), JobType.SERVICE.value()));

      if (isNullOrEmpty(description.getLabels())) {
        description.getLabels().putAll(sourceJob.getLabels());
      }
      if (isNullOrEmpty(description.getEnv())) {
        description.getEnv().putAll(sourceJob.getEnvironment());
      }
      if (isNullOrEmpty(description.getContainerAttributes())) {
        description.getContainerAttributes().putAll(sourceJob.getContainerAttributes());
      }

      configureDisruptionBudget(description, sourceJob, front50Application);

      if (isNullOrEmpty(description.getHardConstraints())) {
        description.setHardConstraints(new ArrayList<>());
      }
      if (isNullOrEmpty(description.getSoftConstraints())) {
        description.setSoftConstraints(new ArrayList<>());
      }
      if (description.getSoftConstraints().isEmpty() && !sourceJob.getSoftConstraints().isEmpty()) {
        sourceJob
            .getSoftConstraints()
            .forEach(
                softConstraint -> {
                  if (!description.getHardConstraints().contains(softConstraint)) {
                    description.getSoftConstraints().add(softConstraint);
                  }
                });
      }
      if (description.getHardConstraints().isEmpty() && !sourceJob.getHardConstraints().isEmpty()) {
        sourceJob
            .getHardConstraints()
            .forEach(
                hardConstraint -> {
                  if (!description.getSoftConstraints().contains(hardConstraint)) {
                    description.getHardConstraints().add(hardConstraint);
                  }
                });
      }

      if (sourceJob.getLabels() != null
          && "false".equals(sourceJob.getLabels().get(USE_APPLICATION_DEFAULT_SG_LABEL))) {
        description.setUseApplicationDefaultSecurityGroup(false);
      }
    } else {
      configureDisruptionBudget(description, null, front50Application);
    }

    state.appendLog(
        "Preparing deployment to %s:%s:%s",
        description.getAccount(),
        description.getRegion(),
        isNullOrEmpty(description.getSubnet()) ? "" : ":" + description.getSubnet());

    DockerImage dockerImage = new DockerImage(description.getImageId());

    if (description.getInterestingHealthProviderNames() != null
        && !description.getInterestingHealthProviderNames().isEmpty()) {
      description
          .getLabels()
          .put(
              "interestingHealthProviderNames",
              String.join(",", description.getInterestingHealthProviderNames()));
    }

    configureAppDefaultSecurityGroup(description);
    Set<String> securityGroups = resolveSecurityGroups(state, description);

    if (JobType.SERVICE.value().equals(description.getJobType())
        && deployDefaults.getAddAppGroupToServerGroup()
        && securityGroups.size() < deployDefaults.getMaxSecurityGroups()
        && description.getUseApplicationDefaultSecurityGroup()) {
      String applicationSecurityGroup =
          awsLookupUtil.convertSecurityGroupNameToId(
              description.getAccount(), description.getRegion(), description.getApplication());
      if (isNullOrEmpty(applicationSecurityGroup)) {
        applicationSecurityGroup =
            (String)
                OperationPoller.retryWithBackoff(
                    op ->
                        awsLookupUtil.createSecurityGroupForApplication(
                            description.getAccount(),
                            description.getRegion(),
                            description.getApplication()),
                    1_000,
                    5);
      }
      securityGroups.add(applicationSecurityGroup);
    }

    SubmitJobRequest submitJobRequest = description.toSubmitJobRequest(dockerImage);

    if (!securityGroups.isEmpty()) {
      submitJobRequest.withSecurityGroups(new ArrayList<>(securityGroups));
    }

    if (front50Application != null && !isNullOrEmpty(front50Application.getEmail())) {
      submitJobRequest.withUser(front50Application.getEmail());
    } else if (!isNullOrEmpty(description.getUser())) {
      submitJobRequest.withUser(description.getUser());
    }

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroupLookupResult = null;
    if (!description.getTargetGroups().isEmpty()) {
      targetGroupLookupResult = validateLoadBalancers(description);
      if (targetGroupLookupResult != null) {
        description
            .getLabels()
            .put(
                LABEL_TARGET_GROUPS,
                String.join(",", targetGroupLookupResult.getTargetGroupARNs()));
      }
    } else if (description.getLabels().containsKey(LABEL_TARGET_GROUPS)) {
      description.getLabels().remove(LABEL_TARGET_GROUPS);
    }

    state.appendLog("Resolving job name");
    String nextServerGroupName =
        resolveJobName(description, submitJobRequest, state.get("titusClient"));
    state.appendLog("Resolved server group name to %s", nextServerGroupName);

    Map<String, Object> result = new HashMap<>();
    result.put("description", description);
    result.put("submitJobRequest", submitJobRequest);
    result.put("nextServerGroupName", nextServerGroupName);
    result.put("targetGroupLookupResult", targetGroupLookupResult);
    return new DefaultStepResult(result);
  }

  private void configureDisruptionBudget(
      TitusDeployDescription description,
      Job sourceJob,
      TitusDeployHandler.Front50Application application) {
    if (description.getDisruptionBudget() == null) {
      // migrationPolicy should only be used when the disruptionBudget has not been specified
      description.setMigrationPolicy(
          orDefault(
              description.getMigrationPolicy(),
              (sourceJob == null) ? null : sourceJob.getMigrationPolicy()));

      // "systemDefault" should be treated as "no migrationPolicy"
      if (description.getMigrationPolicy() == null
          || "systemDefault".equals(description.getMigrationPolicy().getType())) {
        description.setDisruptionBudget(getDefaultDisruptionBudget(application));
      }
    }
  }

  @Nonnull
  private DisruptionBudget getDefaultDisruptionBudget(
      TitusDeployHandler.Front50Application application) {
    DisruptionBudget budget = new DisruptionBudget();
    budget.setAvailabilityPercentageLimit(new AvailabilityPercentageLimit(95));
    budget.setRatePercentagePerInterval(new RatePercentagePerInterval(600_000, 5));
    budget.setTimeWindows(Collections.singletonList(DEFAULT_SYSTEM_TIME_WINDOW));

    if (application != null && application.getPlatformHealthOnly()) {
      budget.setContainerHealthProviders(
          Collections.singletonList(new ContainerHealthProvider("eureka")));
    }

    return budget;
  }

  private void configureAppDefaultSecurityGroup(TitusDeployDescription description) {
    if (description.getLabels().containsKey(USE_APPLICATION_DEFAULT_SG_LABEL)) {
      if ("false".equals(description.getLabels().get(USE_APPLICATION_DEFAULT_SG_LABEL))) {
        description.setUseApplicationDefaultSecurityGroup(false);
      } else {
        description.setUseApplicationDefaultSecurityGroup(true);
      }
    }

    if (!description.getUseApplicationDefaultSecurityGroup()) {
      description.getLabels().put(USE_APPLICATION_DEFAULT_SG_LABEL, "false");
    } else {
      if (description.getLabels().containsKey(USE_APPLICATION_DEFAULT_SG_LABEL)) {
        description.getLabels().remove(USE_APPLICATION_DEFAULT_SG_LABEL);
      }
    }
  }

  @Nullable
  private TargetGroupLookupHelper.TargetGroupLookupResult validateLoadBalancers(
      TitusDeployDescription description) {
    if (description.getTargetGroups().isEmpty()) {
      return null;
    }

    RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
        regionScopedProviderFactory.forRegion(
            (NetflixAmazonCredentials)
                accountCredentialsProvider.getCredentials(
                    description.getCredentials().getAwsAccount()),
            description.getRegion());

    TargetGroupLookupHelper.TargetGroupLookupResult targetGroups =
        targetGroupLookupHelper.getTargetGroupsByName(
            regionScopedProvider, description.getTargetGroups());
    if (!targetGroups.getUnknownTargetGroups().isEmpty()) {
      throw new TargetGroupsNotFoundException(
          format(
              "Unable to find Target Groups: %s",
              String.join(", ", targetGroups.getUnknownTargetGroups())));
    }

    return targetGroups;
  }

  @Nonnull
  private Set<String> resolveSecurityGroups(SagaState state, TitusDeployDescription description) {
    state.appendLog("Resolving Security Groups");

    Set<String> securityGroups = new HashSet<>();
    description
        .getSecurityGroups()
        .forEach(
            providedSecurityGroup -> {
              state.appendLog("Resolving Security Group '%s'", providedSecurityGroup);

              if (awsLookupUtil.securityGroupIdExists(
                  description.getAccount(), description.getRegion(), providedSecurityGroup)) {
                securityGroups.add(providedSecurityGroup);
              } else {
                state.appendLog("Resolving Security Group name '%s'", providedSecurityGroup);
                String convertedSecurityGroup =
                    awsLookupUtil.convertSecurityGroupNameToId(
                        description.getAccount(), description.getRegion(), providedSecurityGroup);
                if (isNullOrEmpty(convertedSecurityGroup)) {
                  throw new SecurityGroupNotFoundException(
                      format("Security Group '%s' cannot be found", providedSecurityGroup));
                }
                securityGroups.add(convertedSecurityGroup);
              }
            });

    state.appendLog("Finished resolving Security Groups");

    return securityGroups;
  }

  private String resolveJobName(
      TitusDeployDescription description,
      SubmitJobRequest submitJobRequest,
      TitusClient titusClient) {
    if (JobType.BATCH.value().equals(submitJobRequest.getJobType())) {
      submitJobRequest.withJobName(description.getApplication());
      return description.getApplication();
    }

    String nextServerGroupName;
    TitusServerGroupNameResolver serverGroupNameResolver =
        new TitusServerGroupNameResolver(titusClient, description.getRegion());
    if (description.getSequence() != null) {
      nextServerGroupName =
          serverGroupNameResolver.generateServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              description.getSequence(),
              false);
    } else {
      nextServerGroupName =
          serverGroupNameResolver.resolveNextServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              false);
    }
    submitJobRequest.withJobName(nextServerGroupName);

    return nextServerGroupName;
  }

  static class SecurityGroupNotFoundException extends TitusException {
    SecurityGroupNotFoundException(String message) {
      super(message);
      setRetryable(false);
    }
  }

  static class TargetGroupsNotFoundException extends TitusException {
    TargetGroupsNotFoundException(String message) {
      super(message);
      setRetryable(true);
    }
  }
}
