package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentLoadBalancerProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj", "region":"ap-guangzhou", "listener":[{"listenerId":"lbl-d2no6v2c", "targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}] }} ]' localhost:7004/tencent/ops
 *
 * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj", "region":"ap-guangzhou", "listener":[{"listenerId":"lbl-hzrdz86n","rules":[{"locationId":"loc-lbcmvnlt","targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}]}] }} ]' localhost:7004/tencent/ops
 */

@Slf4j
class DeleteTencentLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"
  DeleteTencentLoadBalancerDescription description

  @Autowired
  TencentLoadBalancerProvider tencentLoadBalancerProvider

  DeleteTencentLoadBalancerAtomicOperation(DeleteTencentLoadBalancerDescription description) {
    this.description = description
  }

  @Override
   Void operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Initializing delete of Tencent loadBalancer ${description.loadBalancerId} " +
      "in ${description.region}...")
    log.info("params = ${description}")

    def lbListener = description.listener
    if (lbListener?.size() > 0) {    //listener
      lbListener.each {
        def listenerId = it.listenerId
        def rules = it.rules
        def targets = it.targets
        if (rules?.size() > 0) {
          rules.each {
            def ruleTargets = it.targets
            if (ruleTargets?.size() > 0) {    //delete rule's targets
              deleteRuleTargets(description.loadBalancerId, listenerId, it.locationId, ruleTargets)
            }else {  //delete rule
              deleteListenerRule(description.loadBalancerId, listenerId, it)
            }
          }
        }else if (targets?.size() > 0) {    //delete listener's targets
          deleteListenerTargets(description.loadBalancerId, listenerId, targets)
        }else {    //delete listener
          deleteListener(description.loadBalancerId, listenerId)
        }
      }
    }else {    //no listener, delete loadBalancer
      deleteLoadBalancer(description.loadBalancerId)
    }
    return null
  }

  private void deleteLoadBalancer(String loadBalancerId) {
    task.updateStatus(BASE_PHASE, "Start delete loadBalancer ${loadBalancerId} ...")
    def lbClient = new LoadBalancerClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def ret = lbClient.deleteLoadBalancerByIds(loadBalancerId)
    task.updateStatus(BASE_PHASE, "Delete loadBalancer ${loadBalancerId} ${ret} end")
  }

  private void deleteListener(String loadBalancerId, String listenerId) {
    task.updateStatus(BASE_PHASE, "Start delete Listener ${listenerId} ...")
    def lbClient = new LoadBalancerClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def ret = lbClient.deleteLBListenerById(loadBalancerId, listenerId)
    task.updateStatus(BASE_PHASE, "Delete loadBalancer ${listenerId} ${ret} end")
  }

  private void deleteListenerTargets(String loadBalancerId, String listenerId, List<TencentLoadBalancerTarget> targets) {
    task.updateStatus(BASE_PHASE, "Start delete Listener ${listenerId} targets ...")
    def lbClient = new LoadBalancerClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def ret = lbClient.deRegisterTarget4Layer(loadBalancerId, listenerId, targets)
    task.updateStatus(BASE_PHASE, "Delete loadBalancer ${listenerId} targets ${ret} end")
  }

  private void deleteListenerRule(String loadBalancerId, String listenerId, TencentLoadBalancerRule rule) {
    task.updateStatus(BASE_PHASE, "Start delete Listener ${listenerId} rules ...")
    def lbClient = new LoadBalancerClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def rules = [rule]
    def ret = lbClient.deleteLBListenerRules(loadBalancerId, listenerId, rules)
    task.updateStatus(BASE_PHASE, "Delete loadBalancer ${listenerId} rules ${ret} end")
  }

  private void deleteRuleTargets(String loadBalancerId, String listenerId, String locationId, List<TencentLoadBalancerTarget> targets) {
    task.updateStatus(BASE_PHASE, "Start delete Listener ${listenerId} rule ${locationId} targets ...")
    def lbClient = new LoadBalancerClient(
      description.credentials.credentials.secretId,
      description.credentials.credentials.secretKey,
      description.region
    )
    def ret = lbClient.deRegisterTarget7Layer(loadBalancerId, listenerId, locationId, targets)
    task.updateStatus(BASE_PHASE, "Delete loadBalancer ${listenerId} rule ${locationId} targets ${ret} end")
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
