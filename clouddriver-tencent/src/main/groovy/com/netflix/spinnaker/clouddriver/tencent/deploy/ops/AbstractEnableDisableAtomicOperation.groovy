package com.netflix.spinnaker.clouddriver.tencent.deploy.ops


import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription
import groovy.transform.Canonical

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  EnableDisableTencentServerGroupDescription description
  abstract boolean isDisable()
  abstract String getBasePhase()

  AbstractEnableDisableAtomicOperation(EnableDisableTencentServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String basePhase = getBasePhase()

    task.updateStatus basePhase,"Initializing disable server group $description.serverGroupName in $description.region..."


    def serverGroupName = description.serverGroupName
    def region = description.region
    def client = new AutoScalingClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      region
    )

    // find auto scaling group
    def asg = getAutoScalingGroup client, serverGroupName
    def asgId = asg.autoScalingGroupId

    // enable or disable auto scaling group
    enableOrDisableAutoScalingGroup client, asgId

    // get in service instances in auto scaling group
    def inServiceInstanceIds = getInServiceAutoScalingInstances client, asgId

    if (!inServiceInstanceIds) {
      task.updateStatus basePhase, "Auto scaling group has no IN_SERVICE instance. "
      return null
    }

    // enable or disable load balancer
    if (!asg.loadBalancerIdSet && !asg.forwardLoadBalancerSet) {
      task.updateStatus basePhase, "Auto scaling group does not have a load balancer. "
      return null
    }

    enableOrDisableClassicLoadBalancer client, asg, inServiceInstanceIds
    enableOrDisableForwardLoadBalancer client, asg, inServiceInstanceIds

    task.updateStatus basePhase, "Complete enable server group $serverGroupName in $region."
    null
  }

  private def getAutoScalingGroup(AutoScalingClient client, String serverGroupName) {
    def asgs = client.getAutoScalingGroupsByName serverGroupName
    if (asgs) {
      def asg = asgs[0]
      def asgId = asg.autoScalingGroupId
      task.updateStatus basePhase,"Server group $serverGroupName's auto scaling group id is $asgId"
      asg
    } else {
      task.updateStatus basePhase,"Server group $serverGroupName is not found."
      null
    }
  }

  private void enableOrDisableAutoScalingGroup(AutoScalingClient client, String asgId) {
    if (isDisable()) {
      task.updateStatus basePhase, "Disabling auto scaling group $asgId..."
      client.disableAutoScalingGroup asgId
      task.updateStatus basePhase, "Auto scaling group $asgId status is disabled."
    } else {
      task.updateStatus basePhase, "Enabling auto scaling group $asgId..."
      client.enableAutoScalingGroup asgId
      task.updateStatus basePhase, "Auto scaling group $asgId status is enabled."
    }
  }

  private def getInServiceAutoScalingInstances(AutoScalingClient client, String asgId) {
    task.updateStatus basePhase, "Get instances managed by auto scaling group $asgId"
    def instances = client.getAutoScalingInstances asgId

    if (!instances) {
      task.updateStatus basePhase, "Found no instance in $asgId."
      return null
    }

    def inServiceInstanceIds = instances.collect {
      if (it.healthStatus == 'HEALTHY' && it.lifeCycleState == 'IN_SERVICE') {
        it.instanceId
      } else {
        null
      }
    } as List<String>

    task.updateStatus basePhase, "Auto scaling group $asgId has InService instances $inServiceInstanceIds"
    inServiceInstanceIds
  }

  private def enableOrDisableClassicLoadBalancer(client, asg, inServiceInstanceIds) {
    if (!asg.loadBalancerIdSet) {
      return null
    }
    def classicLbs = asg.loadBalancerIdSet
    task.updateStatus basePhase, "Auto scaling group is attached to classic load balancers $classicLbs"

    for (def lbId : classicLbs) {
      if (isDisable()) {
        deregisterInstancesFromClassicalLb client, lbId, inServiceInstanceIds
      } else {
        registerInstancesWithClassicalLb client, lbId, inServiceInstanceIds
      }
    }
  }

  private void deregisterInstancesFromClassicalLb(client, lbId, inServiceInstanceIds) {
    task.updateStatus basePhase, "Start detach instances $inServiceInstanceIds from classic load balancers $lbId"

    def classicLbInstanceIds = client.getClassicLbInstanceIds lbId
    def instanceIds = inServiceInstanceIds.grep classicLbInstanceIds

    if (instanceIds) {
      task.updateStatus basePhase, "Classic load balancer has instances $classicLbInstanceIds " +
        "instances $instanceIds in both auto scaling group and load balancer will be detached from load balancer."
      client.detachAutoScalingInstancesFromClassicClb lbId, instanceIds
    } else {
      task.updateStatus basePhase, "Instances $inServiceInstanceIds are not attached with load balancer $lbId"
    }
    task.updateStatus basePhase, "Finish detach instances $inServiceInstanceIds from classic load balancers $lbId"
  }

  private void registerInstancesWithClassicalLb(client, lbId, inServiceInstanceIds) {
    task.updateStatus basePhase, "Start attach instances $inServiceInstanceIds to classic load balancers $lbId"
    def inServiceClassicTargets = []
    inServiceInstanceIds.each { String instanceId ->
      def target = new Target(
        instanceId: instanceId,
        weight: 10  // default weight 10 for classic lb
      )
      inServiceClassicTargets.add target
    }
    client.attachAutoScalingInstancesToClassicClb lbId, inServiceClassicTargets
    task.updateStatus basePhase, "Finish attach instances $inServiceInstanceIds to classic load balancers $lbId"
  }

  private def enableOrDisableForwardLoadBalancer(client, asg, inServiceInstanceIds) {
    if (!asg.forwardLoadBalancerSet) {
      return null
    }
    def forwardLbs = asg.forwardLoadBalancerSet

    for (def flb : forwardLbs) {
      if (isDisable()) {
        deregisterInstancesFromForwardlLb client, flb, inServiceInstanceIds
      } else {
        registerInstancesWithForwardlLb client, flb, inServiceInstanceIds
      }
    }
  }

  private def deregisterInstancesFromForwardlLb(client, flb, inServiceInstanceIds) {
    def flbId = flb.loadBalancerId
    task.updateStatus basePhase, "Start detach instances $inServiceInstanceIds from forward load balancers $flbId"

    def listeners = client.getForwardLbTargets(flb)
    def forwardLbTargets = []
    def inServiceTargets = []
    inServiceInstanceIds.each { String instanceId ->
      flb.targetAttributes.each { def targetAttribute ->
        def target = new Target(
          instanceId: instanceId,
          port: targetAttribute.port,
          weight: targetAttribute.weight
        )
        inServiceTargets.add target
      }
    }

    listeners.each {
      if (it.protocol == "HTTP" || it.protocol == "HTTPS") {
        it.rules.each { def rule ->
          if (rule.locationId == flb.locationId) {
            for (def flbTarget : rule.targets) {
              forwardLbTargets.add new Target(
                instanceId: flbTarget.instanceId,
                port: flbTarget.port,
                weight: flbTarget.weight
              )
            }
          }
        }
      } else if (it.protocol == "TCP" || it.protocol == "UDP") {
        for (def flbTarget : it.targets) {
          forwardLbTargets.add new Target(
            instanceId: flbTarget.instanceId,
            port: flbTarget.port,
            weight: flbTarget.weight
          )
        }
      } else {
        return
      }
    }

    def targets = inServiceTargets.grep forwardLbTargets
    if (targets) {
      task.updateStatus basePhase, "Forward load balancer has targets $forwardLbTargets " +
        "targets $targets in both auto scaling group and load balancer will be detached from load balancer $flbId."
      client.detachAutoScalingInstancesFromForwardClb flb, targets, true
    } else {
      task.updateStatus basePhase, "Instances $inServiceInstanceIds are not attached with load balancer $flbId"
    }

    task.updateStatus basePhase, "Finish detach instances $inServiceInstanceIds from forward load balancers $flbId"
  }

  private void registerInstancesWithForwardlLb(client, flb, inServiceInstanceIds) {
    def flbId = flb.loadBalancerId
    task.updateStatus basePhase, "Start attach instances $inServiceInstanceIds from forward load balancers $flbId"

    def inServiceTargets = []
    inServiceInstanceIds.each { String instanceId ->
      flb.targetAttributes.each { def targetAttribute ->
        def target = new Target(
          instanceId: instanceId,
          port: targetAttribute.port,
          weight: targetAttribute.weight
        )
        inServiceTargets.add target
      }
    }

    if (inServiceTargets) {
      task.updateStatus basePhase, "In service targets $inServiceTargets will be attached to forward load balancer $flbId"
      client.attachAutoScalingInstancesToForwardClb flb, inServiceTargets, true
    } else {
      task.updateStatus basePhase, "No instances need to be attached to forward load balancer $flbId"
    }
    task.updateStatus basePhase, "Finish attach instances $inServiceInstanceIds from forward load balancers $flbId"
  }

  static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Canonical
  static class Target {
    String instanceId
    Integer weight
    Integer port
  }
}
