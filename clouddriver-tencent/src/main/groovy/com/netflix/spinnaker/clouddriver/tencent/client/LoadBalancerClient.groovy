package com.netflix.spinnaker.clouddriver.tencent.client

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.UpsertTencentLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget
import com.tencentcloudapi.clb.v20180317.models.CertificateInput
import com.tencentcloudapi.clb.v20180317.models.CreateRuleRequest
import com.tencentcloudapi.clb.v20180317.models.CreateRuleResponse
import com.tencentcloudapi.clb.v20180317.models.DeleteListenerRequest
import com.tencentcloudapi.clb.v20180317.models.DeleteListenerResponse
import com.tencentcloudapi.clb.v20180317.models.DeleteLoadBalancerRequest
import com.tencentcloudapi.clb.v20180317.models.DeleteLoadBalancerResponse
import com.tencentcloudapi.clb.v20180317.models.DeleteRuleRequest
import com.tencentcloudapi.clb.v20180317.models.DeleteRuleResponse
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsRequest
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsResponse
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetHealthRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetHealthResponse
import com.tencentcloudapi.clb.v20180317.models.HealthCheck
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend
import com.tencentcloudapi.clb.v20180317.models.LoadBalancerHealth
import com.tencentcloudapi.clb.v20180317.models.ModifyListenerRequest
import com.tencentcloudapi.clb.v20180317.models.ModifyListenerResponse
import com.tencentcloudapi.clb.v20180317.models.ModifyRuleRequest
import com.tencentcloudapi.clb.v20180317.models.ModifyRuleResponse
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsRequest
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsResponse
import com.tencentcloudapi.clb.v20180317.models.RuleInput
import com.tencentcloudapi.clb.v20180317.models.SetLoadBalancerSecurityGroupsRequest
import com.tencentcloudapi.clb.v20180317.models.SetLoadBalancerSecurityGroupsResponse
import com.tencentcloudapi.clb.v20180317.models.Target
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.clb.v20180317.ClbClient
import com.tencentcloudapi.clb.v20180317.models.DescribeLoadBalancersRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeLoadBalancersResponse
import com.tencentcloudapi.clb.v20180317.models.LoadBalancer
import com.tencentcloudapi.clb.v20180317.models.CreateLoadBalancerRequest
import com.tencentcloudapi.clb.v20180317.models.CreateLoadBalancerResponse
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsResponse
import com.tencentcloudapi.clb.v20180317.models.DescribeListenersRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeListenersResponse
import com.tencentcloudapi.clb.v20180317.models.Listener
import com.tencentcloudapi.clb.v20180317.models.CreateListenerResponse
import com.tencentcloudapi.clb.v20180317.models.CreateListenerRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeTaskStatusRequest
import com.tencentcloudapi.clb.v20180317.models.DescribeTaskStatusResponse
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component


@Component
@Slf4j
class LoadBalancerClient {
  private final DEFAULT_LIMIT = 100
  private final MAX_TRY_COUNT = 20
  private final MAX_RULE_TRY_COUNT = 40
  private final REQ_TRY_INTERVAL = 500  //MillSeconds
  private final DESCRIBE_TARGET_HEALTH_LIMIT = 30
  private Credential cred
  ClbClient client

  LoadBalancerClient(String secretId, String secretKey, String region){
    cred = new Credential(secretId, secretKey)
    client = new ClbClient(cred, region)
  }

  List<LoadBalancer> getAllLoadBalancer() {
    List<LoadBalancer> loadBalancerAll = []
    try{
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLimit(DEFAULT_LIMIT)
      req.setForward(1)   //过滤应用型
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      loadBalancerAll.addAll(resp.getLoadBalancerSet())
      def totalCount = resp.getTotalCount()
      def getCount = DEFAULT_LIMIT
      while (totalCount > getCount) {
        req.setOffset(getCount)
        DescribeLoadBalancersResponse respMore = client.DescribeLoadBalancers(req)
        loadBalancerAll.addAll(respMore.getLoadBalancerSet())
        getCount += respMore.getLoadBalancerSet().size()
      }
      return loadBalancerAll
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<LoadBalancer> getLoadBalancerByName(String name) {
    try{
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLimit(DEFAULT_LIMIT)
      req.setForward(1)   //过滤应用型
      req.setLoadBalancerName(name)   //过滤lb name
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return resp.getLoadBalancerSet()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<LoadBalancer> getLoadBalancerById(String id) {
    try{
      DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest();
      req.setLoadBalancerIds(id)
      DescribeLoadBalancersResponse resp = client.DescribeLoadBalancers(req);
      return resp.getLoadBalancerSet()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<String> createLoadBalancer(UpsertTencentLoadBalancerDescription description) {
    try{
      CreateLoadBalancerRequest req = new CreateLoadBalancerRequest();
      req.setLoadBalancerType(description.loadBalancerType)  //OPEN：公网属性， INTERNAL：内网属性
      req.setLoadBalancerName(description.loadBalancerName)
      req.setForward(1) //应用型
      if (description.vpcId?.length() > 0) {
        req.setVpcId(description.vpcId)
      }
      if (description.subnetId?.length() > 0) {
        req.setSubnetId(description.subnetId)
      }
      if (description.projectId != null) {
        req.setProjectId(description.projectId)
      }
      CreateLoadBalancerResponse resp = client.CreateLoadBalancer(req);
      return resp.getLoadBalancerIds()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  String deleteLoadBalancerByIds(String[] loadBalancerIds) {
    try{
      DeleteLoadBalancerRequest req = new DeleteLoadBalancerRequest();
      req.setLoadBalancerIds(loadBalancerIds)
      DeleteLoadBalancerResponse resp = client.DeleteLoadBalancer(req);

      //DescribeTaskStatus is success
      for (def i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<Listener> getAllLBListener(String loadBalancerId) {
    try{
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(loadBalancerId)
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return resp.getListeners()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<Listener> getLBListenerById(String listenerId) {
    try{
      DescribeListenersRequest req = new DescribeListenersRequest();
      req.setLoadBalancerId(listenerId)
      DescribeListenersResponse resp = client.DescribeListeners(req);
      return resp.getListeners()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

 List<String> createLBListener(String loadBalancerId, TencentLoadBalancerListener listener) {
   try{
     CreateListenerRequest req = new CreateListenerRequest();
     req.setLoadBalancerId(loadBalancerId)
     req.setPorts(listener.port)
     req.setProtocol(listener.protocol)
     def listenerName = listener.protocol + listener.port
     if (listener.listenerName?.length() > 0 ) {
        listenerName = listener.listenerName
     }
     req.setListenerNames(listenerName)
     if (listener.protocol in ["TCP","UDP"]) {
       if (listener.sessionExpireTime != null) {
         req.setSessionExpireTime(listener.sessionExpireTime)
       }
       if (listener.scheduler?.length() > 0) {
         req.setScheduler(listener.scheduler)
       }
       if (listener.healthCheck) {  //HealthCheck
          req.healthCheck = new HealthCheck(
            healthSwitch: listener.healthCheck.healthSwitch,
            timeOut: listener.healthCheck.timeOut,
            intervalTime: listener.healthCheck.intervalTime,
            healthNum: listener.healthCheck.healthNum,
            unHealthNum: listener.healthCheck.unHealthNum,
            httpCode: listener.healthCheck.httpCode,
            httpCheckPath: listener.healthCheck.httpCheckPath,
            httpCheckDomain: listener.healthCheck.httpCheckDomain,
            httpCheckMethod: listener.healthCheck.httpCheckMethod )
       }
     }else if (listener.protocol in ["HTTPS"]) {
       if (listener.certificate != null ) {  //cert
         if (listener.certificate.sslMode.equals("UNIDIRECTIONAL")) {
           listener.certificate.certCaId = null   //not need
         }
         req.certificate = new CertificateInput(
           SSLMode: listener.certificate.sslMode,
           certId: listener.certificate.certId,
           certCaId: listener.certificate.certCaId,
           certName: listener.certificate.certName,
           certKey: listener.certificate.certKey,
           certContent: listener.certificate.certContent,
           certCaName: listener.certificate.certCaName,
           certCaContent: listener.certificate.certCaContent
         )
       }
     }

     CreateListenerResponse resp = client.CreateListener(req);

     //DescribeTaskStatus is success
     for (def i = 0; i < MAX_TRY_COUNT; i++) {
       Thread.sleep(REQ_TRY_INTERVAL)
       DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
       statusReq.setTaskId(resp.getRequestId())
       DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
       if (statusResp.getStatus() == 0) {   //task success
         return resp.getListenerIds()
       }
     }
   } catch (TencentCloudSDKException e) {
     throw new TencentOperationException(e.toString())
   }
   return []
  }

  String deleteLBListenerById(String loadBalancerId, String listenerId) {
    try{
      DeleteListenerRequest req = new DeleteListenerRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      DeleteListenerResponse resp = client.DeleteListener(req);

      //DescribeTaskStatus is success
      for (def i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String modifyListener(String loadBalancerId, TencentLoadBalancerListener listener) {
    try{
      def isModify = false
      ModifyListenerRequest req = new ModifyListenerRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listener.listenerId)
      if (listener.healthCheck != null) {
        req.healthCheck = new HealthCheck(
          healthSwitch: listener.healthCheck.healthSwitch,
          timeOut: listener.healthCheck.timeOut,
          intervalTime: listener.healthCheck.intervalTime,
          healthNum: listener.healthCheck.healthNum,
          unHealthNum: listener.healthCheck.unHealthNum,
          httpCode: listener.healthCheck.httpCode,
          httpCheckPath: listener.healthCheck.httpCheckPath,
          httpCheckDomain: listener.healthCheck.httpCheckDomain,
          httpCheckMethod: listener.healthCheck.httpCheckMethod )
        isModify = true
      }
      if (!isModify) {
        return "no modify"
      }
      ModifyListenerResponse resp = client.ModifyListener(req);

      //DescribeTaskStatus is success
      for (def i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String registerTarget4Layer(String loadBalancerId, String listenerId, List<TencentLoadBalancerTarget> targets) {
    try{
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.targets = targets.collect {
        return new Target(instanceId:it.instanceId, port:it.port, type:it.type, weight:it.weight)
      }
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      //DescribeTaskStatus task is success
      def maxTryCount = targets.size() * MAX_TRY_COUNT
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          //return resp.getRequestId()
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String deRegisterTarget4Layer(String loadBalancerId, String listenerId, List<TencentLoadBalancerTarget> targets) {
    try{
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.targets = targets.collect {
        return new Target(instanceId:it.instanceId, port:it.port, type:it.type, weight:it.weight)
      }
      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      //DescribeTaskStatus task is success
      def maxTryCount = targets.size() * MAX_TRY_COUNT
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }// =2
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }
  String createLBListenerRule(String loadBalancerId, String listenerId, TencentLoadBalancerRule rule) {
    try{
      CreateRuleRequest req = new CreateRuleRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      def ruleInput = new RuleInput()
      ruleInput.setDomain(rule.domain)
      ruleInput.setUrl(rule.url)
      if (rule.sessionExpireTime) {
        ruleInput.setSessionExpireTime(rule.sessionExpireTime)
      }
      if (rule.scheduler) {
        ruleInput.setScheduler(rule.scheduler)
      }
      if (rule.healthCheck != null) {
          ruleInput.healthCheck = new HealthCheck(
            healthSwitch: rule.healthCheck.healthSwitch,
            timeOut: rule.healthCheck.timeOut,
            intervalTime: rule.healthCheck.intervalTime,
            healthNum: rule.healthCheck.healthNum,
            unHealthNum: rule.healthCheck.unHealthNum,
            httpCode: rule.healthCheck.httpCode,
            httpCheckPath: rule.healthCheck.httpCheckPath,
            httpCheckDomain: rule.healthCheck.httpCheckDomain,
            httpCheckMethod: rule.healthCheck.httpCheckMethod )
      }
      req.setRules(ruleInput)
      CreateRuleResponse resp = client.CreateRule(req);

      //DescribeTaskStatus task is success
      for (def i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String deleteLBListenerRules(String loadBalancerId, String listenerId, List<TencentLoadBalancerRule> rules) {
    try{
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.LocationIds = rules.collect {
        return new String(it.locationId)
      }
      DeleteRuleResponse resp = client.DeleteRule(req);

      //DescribeTaskStatus task is success
      def maxTryCount = rules.size() * MAX_TRY_COUNT
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String deleteLBListenerRule(String loadBalancerId, String listenerId, String locationId) {
    try{
      DeleteRuleRequest req = new DeleteRuleRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.setLocationIds(locationId)
      DeleteRuleResponse resp = client.DeleteRule(req);

      //DescribeTaskStatus task is success
      for (def i = 0; i < MAX_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String modifyLBListenerRule(String loadBalancerId, String listenerId, TencentLoadBalancerRule rule) {
    try{
      def isModify = false
      ModifyRuleRequest req = new ModifyRuleRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.setLocationId(rule.locationId)

      if (rule.healthCheck != null) {
        req.healthCheck = new HealthCheck(
          healthSwitch: rule.healthCheck.healthSwitch,
          timeOut: rule.healthCheck.timeOut,
          intervalTime: rule.healthCheck.intervalTime,
          healthNum: rule.healthCheck.healthNum,
          unHealthNum: rule.healthCheck.unHealthNum,
          httpCode: rule.healthCheck.httpCode,
          httpCheckPath: rule.healthCheck.httpCheckPath,
          httpCheckDomain: rule.healthCheck.httpCheckDomain,
          httpCheckMethod: rule.healthCheck.httpCheckMethod )
        isModify = true
      }
      if (!isModify) {
        return "no modify"
      }

      ModifyRuleResponse resp = client.ModifyRule(req);

      //DescribeTaskStatus task is success
      for (def i = 0; i < MAX_RULE_TRY_COUNT; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String registerTarget7Layer(String loadBalancerId, String listenerId, String domain, String url, List<TencentLoadBalancerTarget> targets) {
    try{
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.setDomain(domain)
      req.setUrl(url)
      req.targets = targets.collect {
        return new Target(instanceId:it.instanceId, port:it.port, type:it.type, weight:it.weight)
      }
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      //DescribeTaskStatus task is success
      def maxTryCount = targets.size()
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String registerTarget7Layer(String loadBalancerId, String listenerId, String locationId, List<TencentLoadBalancerTarget> targets) {
    try{
      RegisterTargetsRequest req = new RegisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.setLocationId(locationId)
      req.targets = targets.collect {
        return new Target(instanceId:it.instanceId, port:it.port, type:it.type, weight:it.weight)
      }
      RegisterTargetsResponse resp = client.RegisterTargets(req);

      //DescribeTaskStatus task is success
      def maxTryCount = targets.size()
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String deRegisterTarget7Layer(String loadBalancerId, String listenerId, String locationId, List<TencentLoadBalancerTarget> targets) {
    try{
      DeregisterTargetsRequest req = new DeregisterTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerId(listenerId)
      req.setLocationId(locationId)
      req.targets = targets.collect {
        return new Target(instanceId:it.instanceId, port:it.port, type:it.type, weight:it.weight)
      }
      DeregisterTargetsResponse resp = client.DeregisterTargets(req);

      //DescribeTaskStatus task is success
      def maxTryCount = targets.size()
      for (def i = 0; i < maxTryCount; i++) {
        Thread.sleep(REQ_TRY_INTERVAL)
        DescribeTaskStatusRequest statusReq = new  DescribeTaskStatusRequest()
        statusReq.setTaskId(resp.getRequestId())
        DescribeTaskStatusResponse  statusResp = client.DescribeTaskStatus(statusReq)
        if (statusResp.getStatus() == 0) {   //task success
          return "success"
        }
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  List<ListenerBackend> getLBTargets(String loadBalancerId, String listenerId) {
    try{
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.setListenerIds(listenerId)
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return resp.getListeners()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<ListenerBackend> getLBTargetList(String loadBalancerId, List<String> listenerIds) {
    try {
      DescribeTargetsRequest req = new DescribeTargetsRequest();
      req.setLoadBalancerId(loadBalancerId)
      req.listenerIds = listenerIds.collect {
        return new String(it)
      }
      DescribeTargetsResponse resp = client.DescribeTargets(req);
      return resp.getListeners()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  String setLBSecurityGroups(String loadBalancerId, List<String> securityGroups) {
    try {
      SetLoadBalancerSecurityGroupsRequest req = new SetLoadBalancerSecurityGroupsRequest()
      req.setLoadBalancerId(loadBalancerId)
      req.securityGroups = securityGroups.collect {
        return new String(it)
      }
      SetLoadBalancerSecurityGroupsResponse resp = client.SetLoadBalancerSecurityGroups(req)
      return "success"
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<LoadBalancerHealth> getLBTargetHealth(List<String> loadBalancerIds) {
    def loadBalancerHealths = []
    try {
      DescribeTargetHealthRequest req = new DescribeTargetHealthRequest()
      def totalCount = loadBalancerIds.size()
      def reqCount = totalCount
      def startIndex = 0
      def endIndex = DESCRIBE_TARGET_HEALTH_LIMIT
      while(reqCount > 0) {
        if (endIndex > totalCount) {
          endIndex = totalCount
        }
        def batchIds = loadBalancerIds[startIndex..(endIndex-1)]
        req.loadBalancerIds = batchIds.collect {
          return new String(it)
        }
        DescribeTargetHealthResponse resp = client.DescribeTargetHealth(req)
        loadBalancerHealths.addAll(resp.getLoadBalancers())
        reqCount -= DESCRIBE_TARGET_HEALTH_LIMIT
        startIndex += DESCRIBE_TARGET_HEALTH_LIMIT
        endIndex = startIndex + DESCRIBE_TARGET_HEALTH_LIMIT
      }
      return loadBalancerHealths
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

}
