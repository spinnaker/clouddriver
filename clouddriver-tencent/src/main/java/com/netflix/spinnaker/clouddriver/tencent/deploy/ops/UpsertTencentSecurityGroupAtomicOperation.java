package com.netflix.spinnaker.clouddriver.tencent.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.tencent.client.VirtualPrivateCloudClient;
import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule;
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class UpsertTencentSecurityGroupAtomicOperation implements AtomicOperation<Map> {
  private UpsertTencentSecurityGroupAtomicOperation() {}

  public UpsertTencentSecurityGroupAtomicOperation(
      UpsertTencentSecurityGroupDescription description) {
    this.description = description;
  }

  @Override
  public Map operate(List priorOutputs) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Initializing upsert of Tencent securityGroup "
                + getDescription().getSecurityGroupName()
                + " in "
                + getDescription().getRegion()
                + "...");
    log.info("params = " + String.valueOf(getDescription()));

    String securityGroupId = description.getSecurityGroupId();
    if (!StringUtils.isEmpty(securityGroupId)) {
      updateSecurityGroup(description);
    } else {
      insertSecurityGroup(description);
    }

    log.info(
        "upsert securityGroup name:"
            + getDescription().getSecurityGroupName()
            + ", id:"
            + getDescription().getSecurityGroupId());

    return new HashMap() {
      {
        put(
            "securityGroups",
            new HashMap() {
              {
                put(
                    description.getRegion(),
                    new HashMap() {
                      {
                        put("name", description.getSecurityGroupName());
                        put("id", description.getSecurityGroupId());
                      }
                    });
              }
            });
      }
    };
  }

  private String updateSecurityGroup(final UpsertTencentSecurityGroupDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start update securityGroup "
                + description.getSecurityGroupName()
                + " "
                + description.getSecurityGroupId()
                + " ...");
    final String securityGroupId = description.getSecurityGroupId();
    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    SecurityGroupPolicySet oldGroupRules = vpcClient.getSecurityGroupPolicies(securityGroupId);
    final List<TencentSecurityGroupRule> newGroupInRules = description.getInRules();

    // del in rules
    final List<TencentSecurityGroupRule> delGroupInRules = new ArrayList();
    if (oldGroupRules != null && oldGroupRules.getIngress() != null) {
      Arrays.stream(oldGroupRules.getIngress())
          .forEach(
              ingress -> {
                TencentSecurityGroupRule keepRule =
                    newGroupInRules.stream()
                        .filter(
                            it -> {
                              return it.getIndex().equals(ingress.getPolicyIndex());
                            })
                        .findFirst()
                        .orElse(null);
                if (keepRule == null) {
                  TencentSecurityGroupRule delInRule = new TencentSecurityGroupRule();
                  delInRule.setIndex(ingress.getPolicyIndex());
                  delGroupInRules.add(delInRule);
                }
              });
    }
    if (!delGroupInRules.isEmpty()) {
      getTask()
          .updateStatus(BASE_PHASE, "Start delete securityGroup " + securityGroupId + " rules ...");
      vpcClient.deleteSecurityGroupInRules(securityGroupId, delGroupInRules);
      getTask().updateStatus(BASE_PHASE, "delete securityGroup " + securityGroupId + " rules end");
    }

    // add in rules
    final List<TencentSecurityGroupRule> addGroupInRules = new ArrayList();
    if (newGroupInRules != null) {
      newGroupInRules.stream()
          .forEach(
              it -> {
                if (it.getIndex() == null) {
                  addGroupInRules.add(it);
                }
              });
    }
    if (!addGroupInRules.isEmpty()) {
      getTask()
          .updateStatus(BASE_PHASE, "Start add securityGroup " + securityGroupId + " rules ...");
      vpcClient.createSecurityGroupRules(securityGroupId, addGroupInRules, null);
      getTask().updateStatus(BASE_PHASE, "add securityGroup " + securityGroupId + " rules end");
    }

    getTask()
        .updateStatus(
            BASE_PHASE,
            "Update securityGroup "
                + description.getSecurityGroupName()
                + " "
                + description.getSecurityGroupId()
                + " end");
    return "";
  }

  private String insertSecurityGroup(final UpsertTencentSecurityGroupDescription description) {
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Start create new securityGroup " + description.getSecurityGroupName() + " ...");

    VirtualPrivateCloudClient vpcClient =
        new VirtualPrivateCloudClient(
            description.getCredentials().getCredentials().getSecretId(),
            description.getCredentials().getCredentials().getSecretKey(),
            description.getRegion());
    final String securityGroupId =
        vpcClient.createSecurityGroup(
            description.getSecurityGroupName(), description.getSecurityGroupDesc());
    description.setSecurityGroupId(securityGroupId);
    getTask()
        .updateStatus(
            BASE_PHASE,
            "Create new securityGroup "
                + description.getSecurityGroupName()
                + " success, id is "
                + securityGroupId
                + ".");

    if (!CollectionUtils.isEmpty(description.getInRules())) {
      getTask()
          .updateStatus(
              BASE_PHASE, "Start create new securityGroup rules in " + securityGroupId + " ...");
      vpcClient.createSecurityGroupRules(
          securityGroupId, description.getInRules(), description.getOutRules());
      getTask()
          .updateStatus(
              BASE_PHASE, "Create new securityGroup rules in " + securityGroupId + " end");
    }

    return "";
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  public UpsertTencentSecurityGroupDescription getDescription() {
    return description;
  }

  public void setDescription(UpsertTencentSecurityGroupDescription description) {
    this.description = description;
  }

  private static final String BASE_PHASE = "UPSERT_SECURITY_GROUP";
  private UpsertTencentSecurityGroupDescription description;
}
