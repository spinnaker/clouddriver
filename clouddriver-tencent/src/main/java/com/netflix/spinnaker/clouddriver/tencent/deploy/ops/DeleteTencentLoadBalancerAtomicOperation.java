package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.DeleteTencentLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentLoadBalancerProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer":
 * {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj",
 * "region":"ap-guangzhou", "listener":[{"listenerId":"lbl-d2no6v2c",
 * "targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}] }} ]' localhost:7004/tencent/ops
 *
 * <p>curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer":
 * {"application":"myapplication", "account":"account-test","loadBalancerId": "lb-kf2lp6cj",
 * "region":"ap-guangzhou",
 * "listener":[{"listenerId":"lbl-hzrdz86n","rules":[{"locationId":"loc-lbcmvnlt","targets":[{"instanceId":"ins-lq6o6xyc","port":8080}]}]}]
 * }} ]' localhost:7004/tencent/ops
 */
@Slf4j
@ToString
@Data
public class DeleteTencentLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  public DeleteTencentLoadBalancerAtomicOperation(
      DeleteTencentLoadBalancerDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing delete of Tencent loadBalancer "
                + getDescription().getLoadBalancerId()
                + "in "
                + getDescription().getRegion()
                + "...");

    log.info("params = " + getDescription().toString());

    List<TencentLoadBalancerListener> lbListener = description.getListener();
    if (lbListener != null && lbListener.size() > 0) { // listener
      lbListener.stream()
          .forEach(
              it -> {
                final String listenerId = it.getListenerId();
                List<TencentLoadBalancerRule> rules = it.getRules();
                List<TencentLoadBalancerTarget> targets = it.getTargets();
                if (rules.size() > 0) {
                  rules.stream()
                      .forEach(
                          rule -> {
                            List<TencentLoadBalancerTarget> ruleTargets = rule.getTargets();
                            if (ruleTargets.size() > 0) { // delete rule's targets
                              deleteRuleTargets(
                                  getDescription().getLoadBalancerId(),
                                  listenerId,
                                  rule.getLocationId(),
                                  ruleTargets);
                            } else { // delete rule
                              deleteListenerRule(
                                  getDescription().getLoadBalancerId(), listenerId, rule);
                            }
                          });
                } else if (targets.size() > 0) { // delete listener's targets
                  deleteListenerTargets(getDescription().getLoadBalancerId(), listenerId, targets);
                } else { // delete listener
                  deleteListener(getDescription().getLoadBalancerId(), listenerId);
                }
              });
    } else { // no listener, delete loadBalancer
      deleteLoadBalancer(description.getLoadBalancerId());
    }
    return null;
  }

  private void deleteLoadBalancer(final String loadBalancerId) {
    getTask().updateStatus(BASE_PHASE, "Start delete loadBalancer " + loadBalancerId + " ...");
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String ret = lbClient.deleteLoadBalancerByIds(new String[] {loadBalancerId});
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + loadBalancerId + " " + ret + " end");
  }

  private void deleteListener(String loadBalancerId, final String listenerId) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " ...");
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String ret = lbClient.deleteLBListenerById(loadBalancerId, listenerId);
    getTask().updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " " + ret + " end");
  }

  private void deleteListenerTargets(
      String loadBalancerId, final String listenerId, List<TencentLoadBalancerTarget> targets) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " targets ...");
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String ret = lbClient.deRegisterTarget4Layer(loadBalancerId, listenerId, targets);
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " targets " + ret + " end");
  }

  private void deleteListenerRule(
      String loadBalancerId, final String listenerId, TencentLoadBalancerRule rule) {
    getTask().updateStatus(BASE_PHASE, "Start delete Listener " + listenerId + " rules ...");
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    List<TencentLoadBalancerRule> rules =
        new ArrayList<TencentLoadBalancerRule>(Arrays.asList(rule));
    final String ret = lbClient.deleteLBListenerRules(loadBalancerId, listenerId, rules);
    getTask()
        .updateStatus(BASE_PHASE, "Delete loadBalancer " + listenerId + " rules " + ret + " end");
  }

  private void deleteRuleTargets(
      String loadBalancerId,
      final String listenerId,
      final String locationId,
      List<TencentLoadBalancerTarget> targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start delete Listener " + listenerId + " rule " + locationId + " targets ...");
    LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String ret =
        lbClient.deRegisterTarget7Layer(loadBalancerId, listenerId, locationId, targets);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Delete loadBalancer "
                + listenerId
                + " rule "
                + locationId
                + " targets "
                + ret
                + " end");
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER";
  private DeleteTencentLoadBalancerDescription description;
  @Autowired private TencentLoadBalancerProvider tencentLoadBalancerProvider;
}
