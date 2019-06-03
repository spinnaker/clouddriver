package com.netflix.spinnaker.clouddriver.tencent.client

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import com.tencentcloudapi.vpc.v20170312.VpcClient
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupPoliciesRequest
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupPoliciesResponse
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupRequest
import com.tencentcloudapi.vpc.v20170312.models.CreateSecurityGroupResponse
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupPoliciesRequest
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupPoliciesResponse
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupRequest
import com.tencentcloudapi.vpc.v20170312.models.DeleteSecurityGroupResponse
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupPoliciesRequest
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupPoliciesResponse
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupsRequest
import com.tencentcloudapi.vpc.v20170312.models.DescribeSecurityGroupsResponse
import com.tencentcloudapi.vpc.v20170312.models.DescribeSubnetsRequest
import com.tencentcloudapi.vpc.v20170312.models.DescribeSubnetsResponse
import com.tencentcloudapi.vpc.v20170312.models.DescribeVpcsRequest
import com.tencentcloudapi.vpc.v20170312.models.DescribeVpcsResponse
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroup
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicy
import com.tencentcloudapi.vpc.v20170312.models.SecurityGroupPolicySet
import com.tencentcloudapi.vpc.v20170312.models.Subnet
import com.tencentcloudapi.vpc.v20170312.models.Vpc
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Component
@Slf4j
class VirtualPrivateCloudClient {
  private final DEFAULT_LIMIT = 100
  private final String DEFAULT_LIMIT_STR = 100
  private Credential cred
  VpcClient client

  VirtualPrivateCloudClient(String secretId, String secretKey, String region){
    cred = new Credential(secretId, secretKey)
    client = new VpcClient(cred, region)
  }

  String createSecurityGroup(String groupName, String groupDesc) {
    try{
      CreateSecurityGroupRequest req = new CreateSecurityGroupRequest()
      req.setGroupName(groupName)
      if (groupDesc == null) {
        groupDesc = "spinnaker create"
      }
      req.setGroupDescription(groupDesc)
      CreateSecurityGroupResponse resp = client.CreateSecurityGroup(req)
      return resp.securityGroup.securityGroupId
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  String createSecurityGroupRules(String groupId, List<TencentSecurityGroupRule> inRules,
                                  List<TencentSecurityGroupRule> outRules) {
    try{
      CreateSecurityGroupPoliciesRequest req = new CreateSecurityGroupPoliciesRequest()
      req.setSecurityGroupId(groupId)
      if (inRules?.size() > 0) {
        req.securityGroupPolicySet = new SecurityGroupPolicySet()
        req.securityGroupPolicySet.ingress = inRules.collect {
          def ingress = new SecurityGroupPolicy()
          ingress.protocol = it.protocol
          if (!ingress.protocol.equalsIgnoreCase("ICMP")) {  //ICMP not port
            ingress.port = it.port
          }
          ingress.action = it.action
          ingress.cidrBlock = it.cidrBlock
          ingress
        }
      }
      /*
      req.securityGroupPolicySet.egress = outRules.collect {
        def egress = new SecurityGroupPolicy()
        egress.protocol = it.protocol
        egress.port = it.port
        egress.action = it.action
        egress.cidrBlock = it.cidrBlock
        egress
      }*/

      CreateSecurityGroupPoliciesResponse resp = client.CreateSecurityGroupPolicies(req)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  String deleteSecurityGroupInRules(String groupId, List<TencentSecurityGroupRule> inRules) {
    try{
      DeleteSecurityGroupPoliciesRequest req = new DeleteSecurityGroupPoliciesRequest()
      req.setSecurityGroupId(groupId)
      if (inRules?.size() > 0) {
        req.securityGroupPolicySet = new SecurityGroupPolicySet()
        req.securityGroupPolicySet.ingress = inRules.collect {
          def ingress = new SecurityGroupPolicy()
          ingress.policyIndex = it.index
          ingress
        }
      }
      DeleteSecurityGroupPoliciesResponse resp = client.DeleteSecurityGroupPolicies(req)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
    return ""
  }

  List<SecurityGroup> getSecurityGroupsAll() {
    List<SecurityGroup> securityGroupAll = []
    try{
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
      req.setLimit(DEFAULT_LIMIT_STR)
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req)
      securityGroupAll.addAll(resp.getSecurityGroupSet())
      def totalCount = resp.getTotalCount()
      def getCount = DEFAULT_LIMIT
      while(totalCount > getCount) {
        req.setOffset(getCount.toString())
        DescribeSecurityGroupsResponse respMore = client.DescribeSecurityGroups(req)
        securityGroupAll.addAll(respMore.getSecurityGroupSet())
        getCount += respMore.getSecurityGroupSet().size()
      }
      return securityGroupAll
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<SecurityGroup> getSecurityGroupById(String securityGroupId) {
    try{
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest()
      req.setSecurityGroupIds(securityGroupId)
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req)
      return resp.getSecurityGroupSet()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  void deleteSecurityGroup(String securityGroupId) {
    try{
      DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest()
      req.setSecurityGroupId(securityGroupId)
      DeleteSecurityGroupResponse resp = client.DeleteSecurityGroup(req)
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  SecurityGroupPolicySet getSecurityGroupPolicies(String securityGroupId) {
    try{
      DescribeSecurityGroupPoliciesRequest req = new DescribeSecurityGroupPoliciesRequest()
      req.setSecurityGroupId(securityGroupId)
      DescribeSecurityGroupPoliciesResponse resp = client.DescribeSecurityGroupPolicies(req)
      return resp.getSecurityGroupPolicySet()
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<Vpc> getNetworksAll() {
    List<Vpc> networkAll =[]
    try{
      DescribeVpcsRequest req = new DescribeVpcsRequest()
      req.setLimit(DEFAULT_LIMIT_STR)
      DescribeVpcsResponse resp = client.DescribeVpcs(req)
      networkAll.addAll(resp.getVpcSet())
      def totalCount = resp.getTotalCount()
      def getCount = DEFAULT_LIMIT
      while(totalCount > getCount) {
        req.setOffset(getCount.toString())
        DescribeVpcsResponse respMore = client.DescribeVpcs(req)
        networkAll.addAll(respMore.getVpcSet())
        getCount += respMore.getVpcSet().size()
      }
      return networkAll
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  List<Subnet> getSubnetsAll() {
    List<Subnet> subnetAll = []
    try{
      DescribeSubnetsRequest req = new DescribeSubnetsRequest()
      req.setLimit(DEFAULT_LIMIT_STR)
      DescribeSubnetsResponse resp = client.DescribeSubnets(req)
      subnetAll.addAll(resp.getSubnetSet())
      def totalCount = resp.getTotalCount()
      def getCount = DEFAULT_LIMIT
      while(totalCount > getCount) {
        req.setOffset(getCount.toString())
        DescribeSubnetsResponse respMore = client.DescribeSubnets(req)
        subnetAll.addAll(respMore.getSubnetSet())
        getCount += respMore.getSubnetSet().size()
      }
      return subnetAll
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

}
