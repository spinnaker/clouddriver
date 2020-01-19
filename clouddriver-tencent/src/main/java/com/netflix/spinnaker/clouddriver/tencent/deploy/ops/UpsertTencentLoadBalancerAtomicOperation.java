package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.LoadBalancerClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerHealthCheck;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentLoadBalancerProvider;
import com.tencentcloudapi.clb.v20180317.models.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer":
 * {"application":"myapplication", "account":"account-test", "loadBalancerName": "fengCreate5",
 * "region":"ap-guangzhou", "loadBalancerType":"OPEN"
 * ,"listener":[{"listenerName":"listen-create","port":80,"protocol":"TCP",
 * "targets":[{"instanceId":"ins-lq6o6xyc", "port":8080}]}]}} ]' localhost:7004/tencent/ops
 */
@Slf4j
public class UpsertTencentLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  public UpsertTencentLoadBalancerAtomicOperation(
      UpsertTencentLoadBalancerDescription description) {
    this.description = description;
  }

  @Override
  public Map operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of Tencent loadBalancer "
                + getDescription().getLoadBalancerName()
                + " in "
                + getDescription().getRegion()
                + "...");
    // log.info("params = " + String.valueOf(getDescription()));

    String loadBalancerId = description.getLoadBalancerId();
    if (!StringUtils.isEmpty(loadBalancerId)) {
      updateLoadBalancer(description);
    } else { // create new loadBalancer
      insertLoadBalancer(description);
    }

    return new HashMap() {
      {
        put(
            "loadBaalancers",
            new HashMap() {
              {
                put(
                    description.getRegion(),
                    new HashMap() {
                      {
                        put("name", description.getLoadBalancerName());
                      }
                    });
              }
            });
      }
    };
  }

  private String insertLoadBalancer(final UpsertTencentLoadBalancerDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new loadBalancer " + description.getLoadBalancerName() + " ...");

    final LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String loadBalancerId = lbClient.createLoadBalancer(description).get(0);
    try {
      Thread.sleep(3000); // wait for create loadBalancer success
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    List<LoadBalancer> loadBalancer =
        lbClient.getLoadBalancerById(loadBalancerId); // query is create success
    if (loadBalancer.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Create new loadBalancer " + description.getLoadBalancerName() + " failed!");
      return "";
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new loadBalancer "
                + description.getLoadBalancerName()
                + " success, id is "
                + loadBalancerId
                + ".");

    // set securityGroups to loadBalancer
    if (description.getLoadBalancerType().equals("OPEN")
        && description.getSecurityGroups() != null
        && (description.getSecurityGroups().size() > 0)) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Start set securityGroups "
                  + Arrays.toString(description.getSecurityGroups().toArray())
                  + " to loadBalancer "
                  + loadBalancerId
                  + " ...");

      lbClient.setLBSecurityGroups(loadBalancerId, description.getSecurityGroups());
      getTask()
          .updateStatus(BASE_PHASE, "set securityGroups toloadBalancer " + loadBalancerId + " end");
    }

    // create listener
    List<TencentLoadBalancerListener> lbListener = description.getListener();
    if (lbListener.size() > 0) {
      lbListener.stream()
          .forEach(
              it -> {
                insertListener(lbClient, loadBalancerId, it);
              });
    }

    getTask()
        .updateStatus(
            BASE_PHASE, "Create new loadBalancer " + description.getLoadBalancerName() + " end");
    return "";
  }

  private String updateLoadBalancer(final UpsertTencentLoadBalancerDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE, "Start update loadBalancer " + description.getLoadBalancerId() + " ...");

    final LoadBalancerClient lbClient =
        new LoadBalancerClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String loadBalancerId = description.getLoadBalancerId();
    List<LoadBalancer> loadBalancer =
        lbClient.getLoadBalancerById(loadBalancerId); // query is exist
    if (loadBalancer.isEmpty()) {
      getTask().updateStatus(BASE_PHASE, "LoadBalancer " + loadBalancerId + " not exist!");
      return "";
    }

    // update securityGroup
    if (loadBalancer.get(0).getLoadBalancerType().equals("OPEN")) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Start update securityGroups "
                  + Arrays.toString(description.getSecurityGroups().toArray())
                  + " to loadBalancer "
                  + loadBalancerId
                  + " ...");
      lbClient.setLBSecurityGroups(loadBalancerId, description.getSecurityGroups());
      getTask()
          .updateStatus(
              BASE_PHASE, "update securityGroups to loadBalancer " + loadBalancerId + " end");
    }

    final List<TencentLoadBalancerListener> newListeners = description.getListener();

    // get all listeners info
    final List<Listener> queryListeners = lbClient.getAllLBListener(loadBalancerId);
    List<String> listenerIdList =
        queryListeners.stream()
            .map(
                it -> {
                  return it.getListenerId();
                })
            .collect(Collectors.toList());

    final List<ListenerBackend> queryLBTargetList =
        lbClient.getLBTargetList(loadBalancerId, listenerIdList);

    // delete listener
    queryListeners.stream()
        .forEach(
            oldListener -> {
              TencentLoadBalancerListener keepListener =
                  newListeners.stream()
                      .filter(
                          it -> {
                            return it.getListenerId()
                                .equals(((Listener) oldListener).getListenerId());
                          })
                      .findFirst()
                      .orElse(null);

              if (keepListener == null) {
                getTask()
                    .updateStatus(
                        BASE_PHASE,
                        "Start delete listener "
                            + ((Listener) oldListener).getListenerId()
                            + " in "
                            + loadBalancerId
                            + " ...");
                final String ret =
                    lbClient.deleteLBListenerById(
                        loadBalancerId, ((Listener) oldListener).getListenerId());
                getTask()
                    .updateStatus(
                        BASE_PHASE,
                        "Delete listener "
                            + ((Listener) oldListener).getListenerId()
                            + " in "
                            + loadBalancerId
                            + " "
                            + ret
                            + " end");
              }
            });

    // comapre listener
    if (!CollectionUtils.isEmpty(newListeners)) {
      newListeners.stream()
          .forEach(
              inputListener -> {
                if (!StringUtils.isEmpty(inputListener.getListenerId())) {
                  Listener oldListener =
                      queryListeners.stream()
                          .filter(
                              it -> {
                                return it.getListenerId()
                                    .equals(
                                        ((TencentLoadBalancerListener) inputListener)
                                            .getListenerId());
                              })
                          .findFirst()
                          .orElse(null);
                  if (oldListener != null) {
                    ListenerBackend oldTargets =
                        queryLBTargetList.stream()
                            .filter(
                                it -> {
                                  return it.getListenerId()
                                      .equals(
                                          ((TencentLoadBalancerListener) inputListener)
                                              .getListenerId());
                                })
                            .findFirst()
                            .orElse(null);
                    updateListener(
                        lbClient, loadBalancerId, oldListener, inputListener, oldTargets); // modify
                  } else {
                    getTask()
                        .updateStatus(
                            BASE_PHASE,
                            "Input listener " + inputListener.getListenerId() + " not exist!");
                  }
                } else { // not listener id, create new
                  insertListener(lbClient, loadBalancerId, inputListener);
                }
              });
    }

    getTask()
        .updateStatus(
            BASE_PHASE, "Update loadBalancer " + description.getLoadBalancerId() + " end");
    return "";
  }

  private String insertListener(
      final LoadBalancerClient lbClient,
      final String loadBalancerId,
      final TencentLoadBalancerListener listener) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new "
                + listener.getProtocol()
                + " listener in "
                + loadBalancerId
                + " ...");

    final String listenerId = lbClient.createLBListener(loadBalancerId, listener).get(0);
    if (!StringUtils.isEmpty(listenerId)) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Create new "
                  + listener.getProtocol()
                  + " listener in "
                  + loadBalancerId
                  + " success, id is "
                  + listenerId
                  + ".");

      if (Arrays.asList("TCP", "UDP").contains(listener.getProtocol())) { // tcp/udp 4 layer
        List<TencentLoadBalancerTarget> targets = listener.getTargets();
        if (targets.size() > 0) {
          getTask()
              .updateStatus(
                  BASE_PHASE, "Start Register targets to listener " + listenerId + " ...");
          final String ret = lbClient.registerTarget4Layer(loadBalancerId, listenerId, targets);
          getTask()
              .updateStatus(
                  BASE_PHASE, "Register targets to listener " + listenerId + " " + ret + " end.");
        }
      } else if (Arrays.asList("HTTP", "HTTPS")
          .contains(listener.getProtocol())) { // http/https 7 layer
        List<TencentLoadBalancerRule> rules = listener.getRules();
        if (rules.size() > 0) {
          rules.stream()
              .forEach(
                  it -> {
                    insertLBListenerRule(lbClient, loadBalancerId, listenerId, it);
                  });
        }
      }
    } else {
      getTask().updateStatus(BASE_PHASE, "Create new listener failed!");
      return "";
    }
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new " + listener.getProtocol() + " listener in " + loadBalancerId + " end");
    return "";
  }

  private boolean isEqualHealthCheck(
      HealthCheck oldHealth, TencentLoadBalancerHealthCheck newHealth) {
    if ((oldHealth != null) && (newHealth != null)) {
      if (!oldHealth.getHealthSwitch().equals(newHealth.getHealthSwitch())
          || !oldHealth.getTimeOut().equals(newHealth.getTimeOut())
          || !oldHealth.getIntervalTime().equals(newHealth.getIntervalTime())
          || !oldHealth.getHealthNum().equals(newHealth.getHealthNum())
          || !oldHealth.getUnHealthNum().equals(newHealth.getUnHealthNum())
          || !oldHealth.getHttpCode().equals(newHealth.getHttpCode())
          || !oldHealth.getHttpCheckPath().equals(newHealth.getHttpCheckPath())
          || !oldHealth.getHttpCheckDomain().equals(newHealth.getHttpCheckDomain())
          || !oldHealth.getHttpCheckMethod().equals(newHealth.getHttpCheckMethod())) {
        return false;
      }
    }
    return true;
  }

  private boolean isEqualListener(Listener oldListener, TencentLoadBalancerListener newListener) {
    HealthCheck oldHealth = oldListener.getHealthCheck();
    TencentLoadBalancerHealthCheck newHealth = newListener.getHealthCheck();
    if (!isEqualHealthCheck(oldHealth, newHealth)) {
      return false;
    }
    return true;
  }

  private String modifyListenerAttr(
      LoadBalancerClient lbClient,
      final String loadBalancerId,
      final TencentLoadBalancerListener listener) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start modify listener "
                + listener.getListenerId()
                + " attr in "
                + loadBalancerId
                + " ...");
    final String ret = lbClient.modifyListener(loadBalancerId, listener);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "modify listener "
                + listener.getListenerId()
                + " attr in "
                + loadBalancerId
                + " "
                + ret
                + " end");
    return "";
  }

  private String updateListener(
      final LoadBalancerClient lbClient,
      final String loadBalancerId,
      Listener oldListener,
      final TencentLoadBalancerListener newListener,
      final ListenerBackend targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start update listener "
                + newListener.getListenerId()
                + " in "
                + loadBalancerId
                + " ...");

    if (!isEqualListener(oldListener, newListener)) {
      modifyListenerAttr(lbClient, loadBalancerId, newListener);
    }

    final RuleOutput[] oldRules = oldListener.getRules();
    final List<TencentLoadBalancerRule> newRules = newListener.getRules();

    if (Arrays.asList("TCP", "UDP")
        .contains(newListener.getProtocol())) { // tcp/udp 4 layer, targets
      Backend[] oldTargets = targets.getTargets();
      final List<TencentLoadBalancerTarget> newTargets = newListener.getTargets();
      // delete targets
      final List<TencentLoadBalancerTarget> delTargets = new ArrayList<>();
      Arrays.stream(oldTargets)
          .forEach(
              oldTargetEntry -> {
                TencentLoadBalancerTarget keepTarget =
                    newTargets.stream()
                        .filter(
                            it -> {
                              return it.getInstanceId()
                                  .equals(((Backend) oldTargetEntry).getInstanceId());
                            })
                        .findFirst()
                        .orElse(null);
                if (keepTarget == null) {
                  TencentLoadBalancerTarget delTarget =
                      TencentLoadBalancerTarget.builder()
                          .instanceId(oldTargetEntry.getInstanceId())
                          .port(oldTargetEntry.getPort())
                          .type(oldTargetEntry.getType())
                          .weight(oldTargetEntry.getWeight())
                          .build();
                  delTargets.add(delTarget);
                }
              });

      if (!delTargets.isEmpty()) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                "delete listener target in "
                    + loadBalancerId
                    + "."
                    + newListener.getListenerId()
                    + " ...");
        lbClient.deRegisterTarget4Layer(loadBalancerId, newListener.getListenerId(), delTargets);
      }

      // add targets
      final List<TencentLoadBalancerTarget> addTargets = new ArrayList<>();
      newTargets.stream()
          .forEach(
              newTargetEntry -> {
                if (!StringUtils.isEmpty(newTargetEntry.getInstanceId())) {
                  addTargets.add(newTargetEntry);
                }
              });

      if (!addTargets.isEmpty()) {
        getTask()
            .updateStatus(
                BASE_PHASE,
                "add listener target to "
                    + loadBalancerId
                    + "."
                    + newListener.getListenerId()
                    + " ...");
        lbClient.registerTarget4Layer(loadBalancerId, newListener.getListenerId(), addTargets);
      }
    } else if (Arrays.asList("HTTP", "HTTPS")
        .contains(newListener.getProtocol())) { // 7 layer, rules, targets
      Arrays.stream(oldRules)
          .forEach(
              oldRuleEntry -> {
                TencentLoadBalancerRule keepRule =
                    newRules.stream()
                        .filter(
                            it -> {
                              return oldRuleEntry.getLocationId().equals(it.getLocationId());
                            })
                        .findFirst()
                        .orElse(null);
                if (keepRule == null) {
                  lbClient.deleteLBListenerRule(
                      loadBalancerId,
                      newListener.getListenerId(),
                      ((RuleOutput) oldRuleEntry).getLocationId());
                }
              });

      newRules.stream()
          .forEach(
              newRuleEntry -> {
                if (!StringUtils.isEmpty(newRuleEntry.getLocationId())) {
                  RuleOutput oldRule =
                      Arrays.stream(oldRules)
                          .filter(
                              it -> {
                                return newRuleEntry.getLocationId().equals(it.getLocationId());
                              })
                          .findFirst()
                          .orElse(null);

                  if (oldRule != null) { // modify rule
                    RuleTargets ruleTargets =
                        Arrays.stream(targets.getRules())
                            .filter(
                                it -> {
                                  return it.getLocationId().equals(newRuleEntry.getLocationId());
                                })
                            .findFirst()
                            .orElse(null);
                    updateLBListenerRule(
                        lbClient,
                        loadBalancerId,
                        newListener.getListenerId(),
                        oldRule,
                        newRuleEntry,
                        ruleTargets);
                  } else {
                    getTask()
                        .updateStatus(
                            BASE_PHASE,
                            "Input rule " + newRuleEntry.getLocationId() + " not exist!");
                  }

                } else { // create new rule
                  lbClient.createLBListenerRule(
                      loadBalancerId, newListener.getListenerId(), newRuleEntry);
                }
              });
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "update listener " + newListener.getListenerId() + " in " + loadBalancerId + " end");
    return "";
  }

  private boolean isEqualRule(RuleOutput oldRule, TencentLoadBalancerRule newRule) {
    HealthCheck oldHealth = oldRule.getHealthCheck();
    TencentLoadBalancerHealthCheck newHealth = newRule.getHealthCheck();

    if (!isEqualHealthCheck(oldHealth, newHealth)) {
      return false;
    }

    return true;
  }

  private String modifyRuleAttr(
      LoadBalancerClient lbClient,
      final String loadBalancerId,
      final String listenerId,
      final TencentLoadBalancerRule newRule) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start modify rule "
                + newRule.getLocationId()
                + " attr in "
                + loadBalancerId
                + "."
                + listenerId
                + " ...");
    final String ret = lbClient.modifyLBListenerRule(loadBalancerId, listenerId, newRule);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "modify rule "
                + newRule.getLocationId()
                + " attr in "
                + loadBalancerId
                + "."
                + listenerId
                + " "
                + ret
                + " end");
    return "";
  }

  private String updateLBListenerRule(
      LoadBalancerClient lbClient,
      final String loadBalancerId,
      final String listenerId,
      RuleOutput oldRule,
      final TencentLoadBalancerRule newRule,
      RuleTargets targets) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start update rule "
                + newRule.getLocationId()
                + " in "
                + loadBalancerId
                + "."
                + listenerId
                + " ...");

    if (!isEqualRule(oldRule, newRule)) { // modifyRuleAttr()
      modifyRuleAttr(lbClient, loadBalancerId, listenerId, newRule);
    }

    final List<TencentLoadBalancerTarget> newTargets = newRule.getTargets();
    Backend[] oldTargets = targets.getTargets();

    // delete target
    final List<TencentLoadBalancerTarget> delTargets = new ArrayList<>();
    Arrays.stream(oldTargets)
        .forEach(
            oldTargetEntry -> {
              TencentLoadBalancerTarget keepTarget =
                  newTargets.stream()
                      .filter(
                          it -> {
                            return it.getInstanceId().equals(oldTargetEntry.getInstanceId());
                          })
                      .findFirst()
                      .orElse(null);
              if (keepTarget == null) {
                TencentLoadBalancerTarget delTarget =
                    TencentLoadBalancerTarget.builder()
                        .instanceId(oldTargetEntry.getInstanceId())
                        .port(oldTargetEntry.getPort())
                        .type(oldTargetEntry.getType())
                        .weight(oldTargetEntry.getWeight())
                        .build();
                delTargets.add(delTarget);
              }
            });
    if (!delTargets.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "del rule target in "
                  + loadBalancerId
                  + "."
                  + listenerId
                  + "."
                  + newRule.getLocationId()
                  + " ...");
      lbClient.deRegisterTarget7Layer(
          loadBalancerId, listenerId, newRule.getLocationId(), delTargets);
    }

    // add target
    final List<TencentLoadBalancerTarget> addTargets = new ArrayList<>();
    newTargets.stream()
        .forEach(
            newTargetEntry -> {
              if (!StringUtils.isEmpty(newTargetEntry.getInstanceId())) {
                addTargets.add((TencentLoadBalancerTarget) newTargetEntry);
              }
            });

    if (!addTargets.isEmpty()) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "add rule target to "
                  + loadBalancerId
                  + "."
                  + listenerId
                  + "."
                  + newRule.getLocationId()
                  + " ...");
      lbClient.registerTarget7Layer(
          loadBalancerId, listenerId, newRule.getLocationId(), addTargets);
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "update rule "
                + newRule.getLocationId()
                + " in "
                + loadBalancerId
                + "."
                + listenerId
                + " end");
    return "";
  }

  private String insertLBListenerRule(
      LoadBalancerClient lbClient,
      String loadBalancerId,
      final String listenerId,
      final TencentLoadBalancerRule rule) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new rule "
                + rule.getDomain()
                + " "
                + rule.getUrl()
                + " in "
                + listenerId);

    final String ret = lbClient.createLBListenerRule(loadBalancerId, listenerId, rule);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new rule "
                + rule.getDomain()
                + " "
                + rule.getUrl()
                + " in "
                + listenerId
                + " "
                + ret
                + " end.");
    List<TencentLoadBalancerTarget> ruleTargets = rule.getTargets();
    if (ruleTargets.size() > 0) {
      getTask()
          .updateStatus(
              BASE_PHASE, "Start Register targets to listener " + listenerId + " rule ...");
      final String retVal =
          lbClient.registerTarget7Layer(
              loadBalancerId, listenerId, rule.getDomain(), rule.getUrl(), ruleTargets);
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Register targets to listener " + listenerId + " rule " + retVal + " end.");
    }

    return "";
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertTencentLoadBalancerDescription getDescription() {
    return description;
  }

  public void setDescription(UpsertTencentLoadBalancerDescription description) {
    this.description = description;
  }

  public TencentLoadBalancerProvider getTencentLoadBalancerProvider() {
    return tencentLoadBalancerProvider;
  }

  public void setTencentLoadBalancerProvider(
      TencentLoadBalancerProvider tencentLoadBalancerProvider) {
    this.tencentLoadBalancerProvider = tencentLoadBalancerProvider;
  }

  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER";
  private UpsertTencentLoadBalancerDescription description;
  @Autowired private TencentLoadBalancerProvider tencentLoadBalancerProvider;
}
