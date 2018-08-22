/*
 * Copyright 2014 Netflix, Inc.
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

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeLifecycleHooksRequest
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeAsgAtomicOperation implements AtomicOperation<Void> {
  private static final MAX_SIMULTANEOUS_TERMINATIONS = 100
  private static final String PHASE = "RESIZE"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  final ResizeAsgDescription description

  ResizeAsgAtomicOperation(ResizeAsgDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus PHASE, "Initializing Resize ASG operation for $descriptor..."

    for (asg in description.asgs) {
      resizeAsg(asg.serverGroupName, asg.region, asg.capacity, asg.constraints)
    }
    task.updateStatus PHASE, "Finished Resize ASG operation for $descriptor."
    null
  }

  private void resizeAsg(String asgName,
                         String region,
                         ServerGroup.Capacity capacity,
                         ResizeAsgDescription.Constraints constraints) {
    task.updateStatus PHASE, "Beginning resize of ${asgName} in ${region} to ${capacity}."

    if (capacity.min == null && capacity.max == null && capacity.desired == null) {
      task.updateStatus PHASE, "Skipping resize of ${asgName} in ${region}, at least one field in ${capacity} needs to be non-null"
      return
    }

    def autoScaling = amazonClientProvider.getAutoScaling(description.credentials, region, true)
    def describeAutoScalingGroups = autoScaling.describeAutoScalingGroups(
      new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName)
    )
    if (describeAutoScalingGroups.autoScalingGroups.isEmpty() || describeAutoScalingGroups.autoScalingGroups.get(0).status != null) {
      task.updateStatus PHASE, "Skipping resize of ${asgName} in ${region}, server group does not exist"
      return
    }

    def autoScalingGroup = describeAutoScalingGroups.getAutoScalingGroups().get(0)
    validateConstraints(constraints, autoScalingGroup)

    // min, max and desired may be null
    def request = new UpdateAutoScalingGroupRequest().withAutoScalingGroupName(asgName)
        .withMinSize(capacity.min)
        .withMaxSize(capacity.max)
        .withDesiredCapacity(capacity.desired)

    autoScaling.updateAutoScalingGroup request

    if (capacity.desired == 0 && canSafelyTerminateInstances(autoScaling, autoScalingGroup)) {
      // there is an opportunity to expedite a resize to zero by explicitly terminating instances
      // (server group _must not_ be attached to a load balancer nor must it have any termination hooks)
      def amazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, region, true)
      terminateInstancesInAutoScalingGroup(amazonEC2, autoScalingGroup)
    }

    task.updateStatus PHASE, "Completed resize of ${asgName} in ${region}."
  }

  static boolean canSafelyTerminateInstances(AmazonAutoScaling autoScaling, AutoScalingGroup autoScalingGroup) {
    def serverGroupName = autoScalingGroup.getAutoScalingGroupName()
    def noLoadBalancers = autoScalingGroup.loadBalancerNames.isEmpty() && autoScalingGroup.targetGroupARNs.isEmpty()
    def noTerminationHooks = getTerminationHooks(autoScaling, serverGroupName).isEmpty()
    def isSafeToTerminateInstances = noLoadBalancers && noTerminationHooks
    task.updateStatus PHASE, "Server group ${serverGroupName} has no load balancers: ${noLoadBalancers} " +
      "and no termination hooks: ${noTerminationHooks}. Safe to instantly terminate instances: ${isSafeToTerminateInstances}"
    return isSafeToTerminateInstances
  }

  static def getTerminationHooks(AmazonAutoScaling autoScaling, String serverGroupName) {
    return getLifecycleHooks(autoScaling, serverGroupName, "autoscaling:EC2_INSTANCE_TERMINATING")
  }

  static def getLifecycleHooks(AmazonAutoScaling autoScaling, String serverGroupName, String transitionType) {
    DescribeLifecycleHooksRequest request = new DescribeLifecycleHooksRequest()
      .withAutoScalingGroupName(serverGroupName)
    return autoScaling.describeLifecycleHooks(request).getLifecycleHooks().findAll { hook ->
      transitionType.equals(hook.getLifecycleTransition())
    }
  }

  static void terminateInstancesInAutoScalingGroup(AmazonEC2 amazonEC2, AutoScalingGroup autoScalingGroup) {
    def serverGroupName = autoScalingGroup.autoScalingGroupName
    def instanceIds = autoScalingGroup.instances.instanceId

    def terminatedCount = 0
    instanceIds.collate(MAX_SIMULTANEOUS_TERMINATIONS).each {
      try {
        terminatedCount += it.size()
        task.updateStatus PHASE, "Terminating ${terminatedCount} of ${instanceIds.size()} instances in ${serverGroupName}"
        amazonEC2.terminateInstances(new TerminateInstancesRequest().withInstanceIds(it))
      } catch (Exception e) {
        task.updateStatus PHASE, "Unable to terminate instances, reason: '${e.message}'"
      }
    }
  }

  static void validateConstraints(ResizeAsgDescription.Constraints constraints, AutoScalingGroup autoScalingGroup) {
    if (!constraints?.capacity) {
      // no constraints specified, we're ok!
      return
    }

    def current = [
      min    : autoScalingGroup.minSize,
      desired: autoScalingGroup.desiredCapacity,
      max    : autoScalingGroup.maxSize
    ]

    def expected = [
      min    : constraints.capacity.min,
      desired: constraints.capacity.desired,
      max    : constraints.capacity.max
    ]

    boolean hasViolation = expected.find { it.value != null && current[it.key] != it.value }
    if (hasViolation) {
      throw new IllegalStateException(
        String.format(
          "Expected capacity constraint violated (expected: %s, was: %s)",
          expected,
          current,
          autoScalingGroup.autoScalingGroupName
        )
      )
    }
  }
}
