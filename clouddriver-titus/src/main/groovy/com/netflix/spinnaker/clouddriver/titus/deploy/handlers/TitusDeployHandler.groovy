/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent
import com.netflix.spinnaker.clouddriver.saga.DefaultStepResult
import com.netflix.spinnaker.clouddriver.saga.SagaKatoBridgeDsl
import com.netflix.spinnaker.clouddriver.saga.SagaProcessor
import com.netflix.spinnaker.clouddriver.saga.SagaResult
import com.netflix.spinnaker.clouddriver.saga.SingleValueStepResult
import com.netflix.spinnaker.clouddriver.saga.model.SagaState
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.JobType
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.TitusException
import com.netflix.spinnaker.clouddriver.titus.TitusUtils
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.kork.core.RetrySupport
import groovy.util.logging.Slf4j
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.saga.SagaKatoBridgeDsl.StepBuilder.newStep

@Slf4j
@Component
class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {

  private final TitusClientProvider titusClientProvider
  private final AccountCredentialsProvider accountCredentialsProvider
  private final AccountCredentialsRepository accountCredentialsRepository
  private final RegionScopedProviderFactory regionScopedProviderFactory
  private final Front50Service front50Service
  private final AwsConfiguration.DeployDefaults deployDefaults
  private final AwsLookupUtil awsLookupUtil
  private final SagaProcessor operationSagaProcessor
  private final ObjectMapper objectMapper
  private final TargetGroupLookupHelper targetGroupLookupHelper
  private final RetrySupport retrySupport

  @Autowired
  TitusDeployHandler(TitusClientProvider titusClientProvider,
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
      new RetrySupport()
    )
  }

  TitusDeployHandler(TitusClientProvider titusClientProvider,
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
    this.titusClientProvider = titusClientProvider
    this.accountCredentialsProvider = accountCredentialsProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.regionScopedProviderFactory = regionScopedProviderFactory
    this.front50Service = front50Service
    this.deployDefaults = deployDefaults
    this.awsLookupUtil = awsLookupUtil
    this.operationSagaProcessor = operationSagaProcessor
    this.objectMapper = objectMapper
    this.targetGroupLookupHelper = targetGroupLookupHelper
    this.retrySupport = retrySupport
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  TitusDeploymentResult handle(TitusDeployDescription inputDescription, List priorOutputs) {
    // TODO(rz): Would be neat to add an OperationStepProvider that would allow extensions to override stepBuilders?
    def saga = new SagaKatoBridgeDsl()
      .inputs(ImmutableMap.of("description", inputDescription))
      .step(newStep("setup", "initializing").fn({ state ->
        state.put("titusClient", titusClientProvider.getTitusClient(inputDescription.credentials, inputDescription.region))
        new DefaultStepResult()
      }).build())
      .step(newStep("loadFront50App", "loading application attributes").fn({ state ->
        String application = state.getRequired("description", TitusDeployDescription).getApplication()

        try {
          Map front50Application = front50Service.getApplication(application)
          try {
            return new SingleValueStepResult(
              "front50Application",
              objectMapper.convertValue(front50Application, Front50Application.class)
            )
          } catch (IllegalArgumentException e) {
            log.error("Failed to convert front50 application to internal model", e)
            // TODO(rz): user-friendly message
            return new DefaultStepResult(new TitusException(e))
          }
        } catch (Exception e) {
          log.error('Failed to load front50 application attributes for {}', application, e)
          // TODO(rz): user-friendly message
          return new DefaultStepResult(new TitusException(e))
        }
      }).build())
      .step(newStep("prepareDeployment", "preparing deployment")
        .fn(new PrepareDeploymentStep(
          accountCredentialsRepository,
          titusClientProvider,
          awsLookupUtil,
          deployDefaults,
          regionScopedProviderFactory,
          accountCredentialsProvider,
          targetGroupLookupHelper
        )).build())
      .step(newStep("submitJob", "submitting job request to Titus")
        .fn({ state ->
          final TitusClient titusClient = state.getRequired("titusClient")
          final SubmitJobRequest submitJobRequest = state.getRequired("submitJobRequest")
          final TitusDeployDescription description = state.getRequired("description")
          String nextServerGroupName = state.getRequired("nextServerGroupName")

          String jobUri

          int retryCount = 0
          retrySupport.retry({
            try {
              jobUri = titusClient.submitJob(submitJobRequest)
            } catch (StatusRuntimeException e) {
              state.appendLog("Error encountered submitting job request to Titus for $nextServerGroupName: ${e.message}")
              if (isServiceExceptionRetryable(description, e)) {
                if (e.status.description.contains("Job sequence id reserved by another pending job")) {
                  sleep 1000 ^ Math.pow(2, retryCount)
                  retryCount++
                }
                nextServerGroupName = regenerateJobName(state, description, submitJobRequest, titusClient)
                state.appendLog("Retrying with $nextServerGroupName after $retryCount attempts")
                throw e
              }
              if (e.status.code == Status.UNAVAILABLE.code ||
                e.status.code == Status.INTERNAL.code ||
                e.status.code == Status.DEADLINE_EXCEEDED.code) {
                retryCount++
                state.appendLog("Retrying after $retryCount attempts")
                throw e
              } else {
                log.error("Could not submit job and not retrying for status ${e.status} ", e)
                state.appendLog("Could not submit job ${e.status}: ${e.message}")
                throw e
              }
            }
          }, 8, 100, true)

          if (jobUri == null) {
            throw new TitusException("could not create job")
          }

          state.appendLog("Successfully submitted job request to Titus (Job URI: $jobUri)")

          new DefaultStepResult([
            nextServerGroupName: nextServerGroupName,
            serverGroupNames: ["${description.region}:${nextServerGroupName}".toString()],
            serverGroupNameByRegion: [(description.region): nextServerGroupName],
            jobUri: jobUri
          ])
        }).build())
      .step(newStep("copyScalingPolicies", "copy scaling policies")
        .fn(new CopyScalingPoliciesStep(accountCredentialsRepository, titusClientProvider))
        .build())
      .step(newStep("addLoadBalancers", "add load balancers")
        .fn(new AddLoadBalancersStep(titusClientProvider))
        .build())
      .step(newStep("finish", "finishing up").fn({ state ->
        TitusDeployDescription description = state.getRequired("description")
        String nextServerGroupName = state.getRequired("nextServerGroupName")
        String jobUri = state.getRequired("jobUri")

        TitusDeploymentResult deploymentResult = new TitusDeploymentResult()

        // TODO(rz): Surely all of this redundancy is unnecessary?
        if (description.jobType == JobType.BATCH.value()) {
          deploymentResult.deployedNames = [jobUri]
          deploymentResult.deployedNamesByLocation = [(description.region): [jobUri]]
          deploymentResult.jobUri = jobUri
        } else {
          deploymentResult.serverGroupNames = ["${description.getRegion()}:${nextServerGroupName}".toString()]
          deploymentResult.serverGroupNameByRegion = [(description.region): nextServerGroupName]
          deploymentResult.jobUri = jobUri
        }

        deploymentResult.messages = state.getLogs()

        description.events << new CreateServerGroupEvent(
          TitusCloudProvider.ID,
          TitusUtils.getAccountId(accountCredentialsProvider, description.getAccount()),
          description.getRegion(),
          nextServerGroupName
        )

        new SingleValueStepResult("deploymentResult", deploymentResult)
      }).build())
      .build()

    SagaResult<TitusDeploymentResult> result = operationSagaProcessor.process(saga) { finalState ->
      finalState.get("deploymentResult", TitusDeploymentResult)
    }

    if (result.hasError()) {
      throw new TitusException("An error occurred while applying cloud state", result.error)
    }

    result.result
  }

  /**
   * TODO(rz): Not super stoked about this method existing here when virtually the same code exists in
   *           PrepareDeploymentStep, but I'm also getting lazy.
   */
  private String regenerateJobName(SagaState state,
                                   TitusDeployDescription description,
                                   SubmitJobRequest submitJobRequest,
                                   TitusClient titusClient) {
    if (submitJobRequest.getJobType() == 'batch') {
      submitJobRequest.withJobName(description.application)
      return description.application
    }
    String nextServerGroupName
    TitusServerGroupNameResolver serverGroupNameResolver = new TitusServerGroupNameResolver(titusClient, description.region)
    if (description.sequence != null) {
      nextServerGroupName = serverGroupNameResolver.generateServerGroupName(
        description.application,
        description.stack,
        description.freeFormDetails,
        description.sequence,
        false
      )
    } else {
      nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(
        description.application,
        description.stack,
        description.freeFormDetails,
        false
      )
    }
    submitJobRequest.withJobName(nextServerGroupName)

    state.appendLog("Resolved server group name to '%s'", nextServerGroupName)

    return nextServerGroupName
  }

  private static boolean isServiceExceptionRetryable(TitusDeployDescription description, StatusRuntimeException e) {
    return description.jobType == JobType.SERVICE.value() &&
      (e.status.code == Status.RESOURCE_EXHAUSTED.code || e.status.code == Status.INVALID_ARGUMENT.code) &&
      (
        e.status.description.contains("Job sequence id reserved by another pending job") ||
        e.status.description.contains("Constraint violation - job with group sequence")
      )
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription
  }

  static class Front50Application {
    String email;
    Boolean platformHealthOnly;
  }
}
