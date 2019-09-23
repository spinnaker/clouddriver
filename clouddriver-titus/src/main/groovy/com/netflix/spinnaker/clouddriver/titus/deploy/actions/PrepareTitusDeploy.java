/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.titus.deploy.actions;

import static java.lang.String.format;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.saga.SagaCommand;
import com.netflix.spinnaker.clouddriver.saga.flow.SagaAction;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
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
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.LoadFront50App.Front50AppAware;
import com.netflix.spinnaker.clouddriver.titus.deploy.actions.SubmitTitusJob.SubmitTitusJobCommand;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.exceptions.JobNotFoundException;
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage;
import com.netflix.spinnaker.config.AwsConfiguration;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PrepareTitusDeploy extends AbstractTitusDeployAction
    implements SagaAction<PrepareTitusDeploy.PrepareTitusDeployCommand> {
  private static final Logger log = LoggerFactory.getLogger(PrepareTitusDeploy.class);

  private static final TimeWindow DEFAULT_SYSTEM_TIME_WINDOW =
      new TimeWindow(
          Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
          Collections.singletonList(new HourlyTimeWindow(10, 16)),
          "PST");
  private static final String USE_APPLICATION_DEFAULT_SG_LABEL =
      "spinnaker.useApplicationDefaultSecurityGroup";
  private static final String LABEL_TARGET_GROUPS = "spinnaker.targetGroups";

  private final AwsLookupUtil awsLookupUtil;
  private final RegionScopedProviderFactory regionScopedProviderFactory;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AwsConfiguration.DeployDefaults deployDefaults;
  private final TargetGroupLookupHelper targetGroupLookupHelper;

  @Autowired
  public PrepareTitusDeploy(
      AccountCredentialsRepository accountCredentialsRepository,
      TitusClientProvider titusClientProvider,
      AwsLookupUtil awsLookupUtil,
      RegionScopedProviderFactory regionScopedProviderFactory,
      AccountCredentialsProvider accountCredentialsProvider,
      AwsConfiguration.DeployDefaults deployDefaults,
      Optional<TargetGroupLookupHelper> targetGroupLookupHelper) {
    super(accountCredentialsRepository, titusClientProvider);
    this.awsLookupUtil = awsLookupUtil;
    this.regionScopedProviderFactory = regionScopedProviderFactory;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.deployDefaults = deployDefaults;
    this.targetGroupLookupHelper = targetGroupLookupHelper.orElse(new TargetGroupLookupHelper());
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

  @NotNull
  @Override
  public Result apply(@NotNull PrepareTitusDeployCommand command, @NotNull Saga saga) {
    final TitusDeployDescription description = command.description;

    final TitusClient titusClient =
        titusClientProvider.getTitusClient(
            description.getCredentials(), command.description.getRegion());

    final LoadFront50App.Front50App front50App = command.getFront50App();

    final String asgName = description.getSource().getAsgName();
    if (!isNullOrEmpty(asgName)) {
      log.trace("Source present, getting details: {}", asgName);
      mergeSourceDetailsIntoDescription(saga, description, front50App);
    } else {
      configureDisruptionBudget(description, null, front50App);
    }

    saga.log(
        "Preparing deployment to %s:%s:%s",
        description.getAccount(),
        description.getRegion(),
        isNullOrEmpty(description.getSubnet()) ? "" : ":" + description.getSubnet());

    DockerImage dockerImage = new DockerImage(description.getImageId());

    if (!isNullOrEmpty(description.getInterestingHealthProviderNames())) {
      description
          .getLabels()
          .put(
              "interestingHealthProviderNames",
              String.join(",", description.getInterestingHealthProviderNames()));
    }

    configureAppDefaultSecurityGroup(description);

    SubmitJobRequest submitJobRequest = description.toSubmitJobRequest(dockerImage);

    Set<String> securityGroups = resolveSecurityGroups(saga, description);

    if (JobType.isEqual(description.getJobType(), JobType.SERVICE)
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

    if (!securityGroups.isEmpty()) {
      submitJobRequest.withSecurityGroups(new ArrayList<>(securityGroups));
    }

    if (front50App != null && !isNullOrEmpty(front50App.getEmail())) {
      submitJobRequest.withUser(front50App.getEmail());
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
    } else {
      description.getLabels().remove(LABEL_TARGET_GROUPS);
    }

    String nextServerGroupName =
        TitusJobNameResolver.resolveJobName(titusClient, description, submitJobRequest);
    saga.log("Resolved server group name to %s", nextServerGroupName);

    return new Result(
        new SubmitTitusJobCommand(
            saga.getName(),
            saga.getId(),
            description,
            submitJobRequest,
            nextServerGroupName,
            targetGroupLookupResult),
        Collections.emptyList());
  }

  private void configureDisruptionBudget(
      TitusDeployDescription description, Job sourceJob, LoadFront50App.Front50App front50App) {
    if (description.getDisruptionBudget() == null) {
      // migrationPolicy should only be used when the disruptionBudget has not been specified
      description.setMigrationPolicy(
          orDefault(
              description.getMigrationPolicy(),
              (sourceJob == null) ? null : sourceJob.getMigrationPolicy()));

      // "systemDefault" should be treated as "no migrationPolicy"
      if (description.getMigrationPolicy() == null
          || "systemDefault".equals(description.getMigrationPolicy().getType())) {
        description.setDisruptionBudget(getDefaultDisruptionBudget(front50App));
      }
    }
  }

  private void mergeSourceDetailsIntoDescription(
      Saga saga, TitusDeployDescription description, LoadFront50App.Front50App front50App) {
    // If cluster name info was not provided, use the fields from the source asg.
    Names sourceName = Names.parseName(description.getSource().getAsgName());
    description.setApplication(
        description.getApplication() != null ? description.getApplication() : sourceName.getApp());
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
          "Unable to find a Titus client for deployment source: {}",
          description.getSource().getAsgName());
    }

    Job sourceJob = sourceClient.findJobByName(source.getAsgName());
    if (sourceJob == null) {
      throw new JobNotFoundException(
          format(
              "Unable to locate source (%s:%s:%s)",
              source.getAccount(), source.getRegion(), source.getAsgName()));
    }

    saga.log(
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

    if (description.getSource() != null && description.getSource().isUseSourceCapacity()) {
      description.getCapacity().setMin(sourceJob.getInstancesMin());
      description.getCapacity().setMax(sourceJob.getInstancesMax());
      description.getCapacity().setDesired(sourceJob.getInstancesDesired());
    }

    description
        .getResources()
        .setAllocateIpAddress(
            orDefault(
                description.getResources().isAllocateIpAddress(), sourceJob.isAllocateIpAddress()));
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

    configureDisruptionBudget(description, sourceJob, front50App);

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
  }

  @Nonnull
  private DisruptionBudget getDefaultDisruptionBudget(LoadFront50App.Front50App front50App) {
    DisruptionBudget budget = new DisruptionBudget();
    budget.setAvailabilityPercentageLimit(new AvailabilityPercentageLimit(95));
    budget.setRatePercentagePerInterval(new RatePercentagePerInterval(600_000, 5));
    budget.setTimeWindows(Collections.singletonList(DEFAULT_SYSTEM_TIME_WINDOW));

    if (front50App != null && front50App.isPlatformHealthOnly()) {
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
      description.getLabels().remove(USE_APPLICATION_DEFAULT_SG_LABEL);
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
  private Set<String> resolveSecurityGroups(Saga saga, TitusDeployDescription description) {
    saga.log("Resolving Security Groups");

    Set<String> securityGroups = new HashSet<>();
    description
        .getSecurityGroups()
        .forEach(
            providedSecurityGroup -> {
              saga.log("Resolving Security Group '%s'", providedSecurityGroup);

              if (awsLookupUtil.securityGroupIdExists(
                  description.getAccount(), description.getRegion(), providedSecurityGroup)) {
                securityGroups.add(providedSecurityGroup);
              } else {
                saga.log("Resolving Security Group name '%s'", providedSecurityGroup);
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

    saga.log("Finished resolving Security Groups");

    return securityGroups;
  }

  @EqualsAndHashCode(callSuper = true)
  @Value
  public static class PrepareTitusDeployCommand extends SagaCommand implements Front50AppAware {
    private final TitusDeployDescription description;
    @NonFinal private LoadFront50App.Front50App front50App;

    public PrepareTitusDeployCommand(
        @NotNull String sagaName, @NotNull String sagaId, TitusDeployDescription description) {
      super(sagaName, sagaId);
      this.description = description;
    }

    @Override
    public void setFront50App(@Nonnull LoadFront50App.Front50App app) {
      this.front50App = app;
    }
  }

  private static class SecurityGroupNotFoundException extends TitusException {
    SecurityGroupNotFoundException(String message) {
      super(message);
      setRetryable(false);
    }
  }

  private static class TargetGroupsNotFoundException extends TitusException {
    TargetGroupsNotFoundException(String message) {
      super(message);
      setRetryable(true);
    }
  }
}
