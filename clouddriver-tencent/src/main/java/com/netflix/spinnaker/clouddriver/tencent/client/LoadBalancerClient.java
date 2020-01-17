package com.netflix.spinnaker.clouddriver.tencent.client;

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget;
import com.tencentcloudapi.clb.v20180317.ClbClient;
import com.tencentcloudapi.clb.v20180317.models.*;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
@Data
public class LoadBalancerClient {
  public LoadBalancerClient(String secretId, String secretKey, String region) {
    cred = new Credential(secretId, secretKey);
    client = new ClbClient(cred, region);
  }

  public List<LoadBalancer> getAllLoadBalancer() {
    List<LoadBalancer> loadBalancerAll = new ArrayList<LoadBalancer>();
    try {
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLimit(DEFAULT_LIMIT);
      req.setForward(1); // 过滤应用型
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      Collections.addAll(loadBalancerAll, resp.getLoadBalancerSet());
      Integer totalCount = resp.getTotalCount();
      Integer getCount = DEFAULT_LIMIT;
      while (totalCount > getCount) {
        req.setOffset(getCount);
        DescribeLoadBalancersResponse respMore = client.DescribeLoadBalancers(req);
        Collections.addAll(loadBalancerAll, respMore.getLoadBalancerSet());
        getCount += respMore.getLoadBalancerSet().length;
      }
      return loadBalancerAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<LoadBalancer> getLoadBalancerByName(String name) {
    try {
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLimit(DEFAULT_LIMIT);
      req.setForward(1); // 过滤应用型
      req.setLoadBalancerName(name); // 过滤lb name
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return Arrays.asList(resp.getLoadBalancerSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<LoadBalancer> getLoadBalancerById(String id) {
    try {
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLoadBalancerIds(new String[] {id});
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return Arrays.asList(resp.getLoadBalancerSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<String> createLoadBalancer(UpsertTencentLoadBalancerDescription description) {
    try {
      CreateLoadBalancerRequest req = new CreateLoadBalancerRequest();
      req.setLoadBalancerType(description.getLoadBalancerType()); // OPEN：公网属性， INTERNAL：内网属性
      req.setLoadBalancerName(description.getLoadBalancerName());
      req.setForward(1); // 应用型
      if (description.getVpcId().length() > 0) {
        req.setVpcId(description.getVpcId());
      }

      if (description.getSubnetId().length() > 0) {
        req.setSubnetId(description.getSubnetId());
      }

      if (description.getProjectId() != null) {
        req.setProjectId(description.getProjectId());
      }

      CreateLoadBalancerResponse resp = client.CreateLoadBalancer(req);
      return Arrays.asList(resp.getLoadBalancerIds());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public String deleteLoadBalancerByIds(String[] loadBalancerIds) {
    try {
      DeleteLoadBalancerRequest req = new DeleteLoadBalancerRequest();
      req.setLoadBalancerIds(loadBalancerIds);
      DeleteLoadBalancerResponse resp = client.DeleteLoadBalancer(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    } catch (InterruptedException e) {
      e.printStackTrace();
      log.error("deleteLoadBalancerByIds error ", e);
    }
    return "fail";
  }

  public List<Listener> getAllLBListener(String loadBalancerId) {
    try {
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(loadBalancerId);
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<Listener> getLBListenerById(String listenerId) {
    try {
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(listenerId);
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<String> createLBListener(
      String loadBalancerId, TencentLoadBalancerListener listener) {
    try {
      CreateListenerRequest req = new CreateListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setPorts(new Integer[] {listener.getPort()});
      req.setProtocol(listener.getProtocol());
      String listenerName = listener.getProtocol() + listener.getPort();
      if (!StringUtils.isEmpty(listener.getListenerName())) {
        listenerName = listener.getListenerName();
      }

      req.setListenerNames(new String[] {listenerName});
      if (Arrays.asList("TCP", "UDP").contains(listener.getProtocol())) {
        if (listener.getSessionExpireTime() != null) {
          req.setSessionExpireTime(listener.getSessionExpireTime());
        }

        if (listener.getScheduler().length() > 0) {
          req.setScheduler(listener.getScheduler());
        }

        if (listener.getHealthCheck() != null) { // HealthCheck
          HealthCheck check = new HealthCheck();
          check.setHealthSwitch(listener.getHealthCheck().getHealthSwitch());
          check.setTimeOut(listener.getHealthCheck().getTimeOut());
          check.setIntervalTime(listener.getHealthCheck().getIntervalTime());
          check.setHealthNum(listener.getHealthCheck().getHealthNum());
          check.setUnHealthNum(listener.getHealthCheck().getUnHealthNum());
          check.setHttpCode(listener.getHealthCheck().getHttpCode());
          check.setHttpCheckPath(listener.getHealthCheck().getHttpCheckPath());
          check.setHttpCheckDomain(listener.getHealthCheck().getHttpCheckDomain());
          check.setHttpCheckMethod(listener.getHealthCheck().getHttpCheckMethod());
          req.setHealthCheck(check);
        }
      } else if (Arrays.asList("HTTPS").contains(listener.getProtocol())) {
        if (listener.getCertificate() != null) { // cert
          if (listener.getCertificate().getSslMode().equals("UNIDIRECTIONAL")) {
            listener.getCertificate().setCertCaId(null); // not need
          }

          CertificateInput input = new CertificateInput();
          input.setSSLMode(listener.getCertificate().getSslMode());
          input.setCertId(listener.getCertificate().getCertId());
          input.setCertCaId(listener.getCertificate().getCertCaId());
          input.setCertName(listener.getCertificate().getCertName());
          input.setCertKey(listener.getCertificate().getCertKey());
          input.setCertContent(listener.getCertificate().getCertContent());
          input.setCertCaName(listener.getCertificate().getCertCaName());
          input.setCertCaContent(listener.getCertificate().getCertCaContent());
          req.setCertificate(input);
        }
      }

      CreateListenerResponse resp = client.CreateListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return Arrays.asList(resp.getListenerIds());
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return new ArrayList<String>();
  }

  public String deleteLBListenerById(String loadBalancerId, String listenerId) {
    try {
      DeleteListenerRequest req = new DeleteListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      DeleteListenerResponse resp = client.DeleteListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String modifyListener(String loadBalancerId, TencentLoadBalancerListener listener) {
    try {
      Boolean isModify = false;
      ModifyListenerRequest req = new ModifyListenerRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listener.getListenerId());
      if (listener.getHealthCheck() != null) {
        HealthCheck check = new HealthCheck();
        check.setHealthSwitch(listener.getHealthCheck().getHealthSwitch());
        check.setTimeOut(listener.getHealthCheck().getTimeOut());
        check.setIntervalTime(listener.getHealthCheck().getIntervalTime());
        check.setHealthNum(listener.getHealthCheck().getHealthNum());
        check.setUnHealthNum(listener.getHealthCheck().getUnHealthNum());
        check.setHttpCode(listener.getHealthCheck().getHttpCode());
        check.setHttpCheckPath(listener.getHealthCheck().getHttpCheckPath());
        check.setHttpCheckDomain(listener.getHealthCheck().getHttpCheckDomain());
        check.setHttpCheckMethod(listener.getHealthCheck().getHttpCheckMethod());
        req.setHealthCheck(check);
        isModify = true;
      }

      if (!isModify) {
        return "no modify";
      }

      ModifyListenerResponse resp = client.ModifyListener(req);

      // DescribeTaskStatus is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String registerTarget4Layer(
      String loadBalancerId, String listenerId, List<TencentLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setTargets(
          targets.stream()
              .map(
                  it -> {
                    Target target = new Target();
                    target.setInstanceId(it.getInstanceId());
                    target.setPort(it.getPort());
                    target.setType(it.getType());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(Target[]::new));

      RegisterTargetsResponse resp = client.RegisterTargets(req);
      // DescribeTaskStatus task is success
      int maxTryCount = targets.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          // return resp.getRequestId()
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String deRegisterTarget4Layer(
      String loadBalancerId, String listenerId, List<TencentLoadBalancerTarget> targets) {
    try {
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setTargets(
          targets.stream()
              .map(
                  it -> {
                    Target target = new Target();
                    target.setInstanceId(it.getInstanceId());
                    target.setPort(it.getPort());
                    target.setType(it.getType());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(Target[]::new));
      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
        // =2
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String createLBListenerRule(
      String loadBalancerId, String listenerId, TencentLoadBalancerRule rule) {
    try {
      CreateRuleRequest req = new CreateRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      RuleInput ruleInput = new RuleInput();
      ruleInput.setDomain(rule.getDomain());
      ruleInput.setUrl(rule.getUrl());
      if (rule.getSessionExpireTime() > 0) {
        ruleInput.setSessionExpireTime(rule.getSessionExpireTime());
      }

      if (!StringUtils.isEmpty(rule.getScheduler())) {
        ruleInput.setScheduler(rule.getScheduler());
      }

      if (rule.getHealthCheck() != null) {
        HealthCheck check = new HealthCheck();
        check.setHealthSwitch(rule.getHealthCheck().getHealthSwitch());
        check.setTimeOut(rule.getHealthCheck().getTimeOut());
        check.setIntervalTime(rule.getHealthCheck().getIntervalTime());
        check.setHealthNum(rule.getHealthCheck().getHealthNum());
        check.setUnHealthNum(rule.getHealthCheck().getUnHealthNum());
        check.setHttpCode(rule.getHealthCheck().getHttpCode());
        check.setHttpCheckPath(rule.getHealthCheck().getHttpCheckPath());
        check.setHttpCheckDomain(rule.getHealthCheck().getHttpCheckDomain());
        check.setHttpCheckMethod(rule.getHealthCheck().getHttpCheckMethod());

        ruleInput.setHealthCheck(check);
      }

      req.setRules(new RuleInput[] {ruleInput});
      CreateRuleResponse resp = client.CreateRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String deleteLBListenerRules(
      String loadBalancerId, String listenerId, List<TencentLoadBalancerRule> rules) {
    try {
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationIds(rules.stream().map(it -> it.getLocationId()).toArray(String[]::new));

      DeleteRuleResponse resp = client.DeleteRule(req);

      // DescribeTaskStatus task is success
      int maxTryCount = rules.size() * MAX_TRY_COUNT;
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String deleteLBListenerRule(String loadBalancerId, String listenerId, String locationId) {
    try {
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationIds(new String[] {locationId});
      DeleteRuleResponse resp = client.DeleteRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String modifyLBListenerRule(
      String loadBalancerId, String listenerId, TencentLoadBalancerRule rule) {
    try {
      Boolean isModify = false;
      ModifyRuleRequest req = new ModifyRuleRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(rule.getLocationId());

      if (rule.getHealthCheck() != null) {
        HealthCheck check = new HealthCheck();
        check.setHealthSwitch(rule.getHealthCheck().getHealthSwitch());
        check.setTimeOut(rule.getHealthCheck().getTimeOut());
        check.setIntervalTime(rule.getHealthCheck().getIntervalTime());
        check.setHealthNum(rule.getHealthCheck().getHealthNum());
        check.setUnHealthNum(rule.getHealthCheck().getUnHealthNum());
        check.setHttpCode(rule.getHealthCheck().getHttpCode());
        check.setHttpCheckPath(rule.getHealthCheck().getHttpCheckPath());
        check.setHttpCheckDomain(rule.getHealthCheck().getHttpCheckDomain());
        check.setHttpCheckMethod(rule.getHealthCheck().getHttpCheckMethod());

        req.setHealthCheck(check);
        isModify = true;
      }

      if (!isModify) {
        return "no modify";
      }
      ModifyRuleResponse resp = client.ModifyRule(req);

      // DescribeTaskStatus task is success
      for (int i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String registerTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String domain,
      String url,
      List<TencentLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setDomain(domain);
      req.setUrl(url);
      req.setTargets(
          targets.stream()
              .map(
                  it -> {
                    Target target = new Target();
                    target.setInstanceId(it.getInstanceId());
                    target.setPort(it.getPort());
                    target.setType(it.getType());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(Target[]::new));
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String registerTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String locationId,
      List<TencentLoadBalancerTarget> targets) {
    try {
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(locationId);
      req.setTargets(
          targets.stream()
              .map(
                  it -> {
                    Target target = new Target();
                    target.setInstanceId(it.getInstanceId());
                    target.setPort(it.getPort());
                    target.setType(it.getType());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(Target[]::new));
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (Integer i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public String deRegisterTarget7Layer(
      String loadBalancerId,
      String listenerId,
      String locationId,
      List<TencentLoadBalancerTarget> targets) {
    try {
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerId(listenerId);
      req.setLocationId(locationId);
      req.setTargets(
          targets.stream()
              .map(
                  it -> {
                    Target target = new Target();
                    target.setInstanceId(it.getInstanceId());
                    target.setPort(it.getPort());
                    target.setType(it.getType());
                    target.setWeight(it.getWeight());
                    return target;
                  })
              .toArray(Target[]::new));
      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      // DescribeTaskStatus task is success
      int maxTryCount = targets.size();
      for (int i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL);
        DescribeTaskStatusRequest statusReq = new DescribeTaskStatusRequest();
        statusReq.setTaskId(resp.getRequestId());
        DescribeTaskStatusResponse statusResp = client.DescribeTaskStatus(statusReq);
        if (statusResp.getStatus() == 0) { // task success
          return "success";
        }
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public List<ListenerBackend> getLBTargets(String loadBalancerId, String listenerId) {
    try {
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerIds(new String[] {listenerId});
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<ListenerBackend> getLBTargetList(String loadBalancerId, List<String> listenerIds) {
    try {
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setListenerIds(listenerIds.stream().toArray(String[]::new));
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return Arrays.asList(resp.getListeners());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public String setLBSecurityGroups(String loadBalancerId, List<String> securityGroups) {
    try {
      SetLoadBalancerSecurityGroupsRequest req = new SetLoadBalancerSecurityGroupsRequest();
      req.setLoadBalancerId(loadBalancerId);
      req.setSecurityGroups(securityGroups.stream().toArray(String[]::new));
      SetLoadBalancerSecurityGroupsResponse resp = client.SetLoadBalancerSecurityGroups(req);
      return "success";
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<LoadBalancerHealth> getLBTargetHealth(List<String> loadBalancerIds) {
    List<LoadBalancerHealth> loadBalancerHealths = new ArrayList<>();
    try {
      DescribeTargetHealthRequest req = new DescribeTargetHealthRequest();
      int totalCount = loadBalancerIds.size();
      int reqCount = totalCount;
      int startIndex = 0;
      int endIndex = DESCRIBE_TARGET_HEALTH_LIMIT;
      log.info("getLBTargetHealth loadBalancerIds size = {}", loadBalancerIds.size());
      while (reqCount > 0) {
        if (endIndex > totalCount) {
          endIndex = totalCount;
        }

        List<String> batchIds =
            loadBalancerIds.stream()
                .skip(startIndex)
                .limit(endIndex - startIndex)
                .collect(Collectors.toList());
        log.info("getLBTargetHealth batchIds = {}", Strings.join(batchIds, ','));
        req.setLoadBalancerIds(batchIds.stream().toArray(String[]::new));
        DescribeTargetHealthResponse resp = client.DescribeTargetHealth(req);
        Collections.addAll(loadBalancerHealths, resp.getLoadBalancers());
        reqCount -= DESCRIBE_TARGET_HEALTH_LIMIT;
        startIndex += DESCRIBE_TARGET_HEALTH_LIMIT;
        endIndex = startIndex + DESCRIBE_TARGET_HEALTH_LIMIT;
      }
      return loadBalancerHealths;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public ClbClient getClient() {
    return client;
  }

  public void setClient(ClbClient client) {
    this.client = client;
  }

  private final int DEFAULT_LIMIT = 100;
  private final int MAX_TRY_COUNT = 20;
  private final int MAX_RULE_TRY_COUNT = 40;
  private final int REQ_TRY_INTERVAL = 500;
  private final int DESCRIBE_TARGET_HEALTH_LIMIT = 30;
  private Credential cred;
  private ClbClient client;
}
