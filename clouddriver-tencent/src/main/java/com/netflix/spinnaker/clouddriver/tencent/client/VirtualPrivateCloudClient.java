package com.netflix.spinnaker.clouddriver.tencent.client;

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentSecurityGroupRule;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vpc.v20170312.VpcClient;
import com.tencentcloudapi.vpc.v20170312.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Data
public class VirtualPrivateCloudClient {
  public VirtualPrivateCloudClient(String secretId, String secretKey, String region) {
    cred = new Credential(secretId, secretKey);
    client = new VpcClient(cred, region);
  }

  public String createSecurityGroup(String groupName, String groupDesc) {
    try {
      CreateSecurityGroupRequest req = new CreateSecurityGroupRequest();
      req.setGroupName(groupName);
      if (groupDesc == null) {
        groupDesc = "spinnaker create";
      }

      req.setGroupDescription(groupDesc);
      CreateSecurityGroupResponse resp = client.CreateSecurityGroup(req);
      return resp.getSecurityGroup().getSecurityGroupId();
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public String createSecurityGroupRules(
      String groupId,
      List<TencentSecurityGroupRule> inRules,
      List<TencentSecurityGroupRule> outRules) {
    try {
      CreateSecurityGroupPoliciesRequest req = new CreateSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(groupId);
      if (inRules != null && inRules.size() > 0) {
        req.setSecurityGroupPolicySet(new SecurityGroupPolicySet());
        req.getSecurityGroupPolicySet()
            .setIngress(
                inRules.stream()
                    .map(
                        it -> {
                          SecurityGroupPolicy ingress = new SecurityGroupPolicy();
                          ingress.setProtocol(it.getProtocol());
                          if (!ingress.getProtocol().equalsIgnoreCase("ICMP")) { // ICMP not port
                            ingress.setPort(it.getPort());
                          }

                          ingress.setAction(it.getAction());
                          ingress.setCidrBlock(it.getCidrBlock());
                          return ingress;
                        })
                    .toArray(SecurityGroupPolicy[]::new));
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

      CreateSecurityGroupPoliciesResponse resp = client.CreateSecurityGroupPolicies(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }

    return "";
  }

  public String deleteSecurityGroupInRules(String groupId, List<TencentSecurityGroupRule> inRules) {
    try {
      DeleteSecurityGroupPoliciesRequest req = new DeleteSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(groupId);
      if (inRules.size() > 0) {
        req.setSecurityGroupPolicySet(new SecurityGroupPolicySet());
        req.getSecurityGroupPolicySet()
            .setIngress(
                inRules.stream()
                    .map(
                        it -> {
                          SecurityGroupPolicy ingress = new SecurityGroupPolicy();
                          ingress.setPolicyIndex(it.getIndex());
                          return ingress;
                        })
                    .toArray(SecurityGroupPolicy[]::new));
      }
      DeleteSecurityGroupPoliciesResponse resp = client.DeleteSecurityGroupPolicies(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
    return "";
  }

  public List<SecurityGroup> getSecurityGroupsAll() {
    List<SecurityGroup> securityGroupAll = new ArrayList<SecurityGroup>();
    try {
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req);
      Collections.addAll(securityGroupAll, resp.getSecurityGroupSet());
      Integer totalCount = resp.getTotalCount();
      Integer getCount = DEFAULT_LIMIT;
      while (totalCount > getCount) {
        req.setOffset(getCount.toString());
        DescribeSecurityGroupsResponse respMore = client.DescribeSecurityGroups(req);
        Collections.addAll(securityGroupAll, respMore.getSecurityGroupSet());
        getCount += respMore.getSecurityGroupSet().length;
      }

      return securityGroupAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<SecurityGroup> getSecurityGroupById(String securityGroupId) {
    try {
      DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();
      req.setSecurityGroupIds(new String[] {securityGroupId});
      DescribeSecurityGroupsResponse resp = client.DescribeSecurityGroups(req);
      return Arrays.asList(resp.getSecurityGroupSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void deleteSecurityGroup(String securityGroupId) {
    try {
      DeleteSecurityGroupRequest req = new DeleteSecurityGroupRequest();
      req.setSecurityGroupId(securityGroupId);
      DeleteSecurityGroupResponse resp = client.DeleteSecurityGroup(req);
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public SecurityGroupPolicySet getSecurityGroupPolicies(String securityGroupId) {
    try {
      DescribeSecurityGroupPoliciesRequest req = new DescribeSecurityGroupPoliciesRequest();
      req.setSecurityGroupId(securityGroupId);
      DescribeSecurityGroupPoliciesResponse resp = client.DescribeSecurityGroupPolicies(req);
      return resp.getSecurityGroupPolicySet();
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<Vpc> getNetworksAll() {
    List<Vpc> networkAll = new ArrayList<Vpc>();
    try {
      DescribeVpcsRequest req = new DescribeVpcsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeVpcsResponse resp = client.DescribeVpcs(req);
      Collections.addAll(networkAll, resp.getVpcSet());
      int totalCount = resp.getTotalCount();
      Integer getCount = DEFAULT_LIMIT;
      while (totalCount > getCount) {
        req.setOffset(getCount.toString());
        DescribeVpcsResponse respMore = client.DescribeVpcs(req);
        Collections.addAll(networkAll, respMore.getVpcSet());
        getCount += respMore.getVpcSet().length;
      }
      return networkAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<Subnet> getSubnetsAll() {
    List<Subnet> subnetAll = new ArrayList<Subnet>();
    try {
      DescribeSubnetsRequest req = new DescribeSubnetsRequest();
      req.setLimit(DEFAULT_LIMIT_STR);
      DescribeSubnetsResponse resp = client.DescribeSubnets(req);
      Collections.addAll(subnetAll, resp.getSubnetSet());
      int totalCount = resp.getTotalCount();
      Integer getCount = DEFAULT_LIMIT;
      while (totalCount > getCount) {
        req.setOffset(getCount.toString());
        DescribeSubnetsResponse respMore = client.DescribeSubnets(req);
        Collections.addAll(subnetAll, respMore.getSubnetSet());
        getCount += respMore.getSubnetSet().length;
      }
      return subnetAll;
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public VpcClient getClient() {
    return client;
  }

  public void setClient(VpcClient client) {
    this.client = client;
  }

  private final int DEFAULT_LIMIT = 100;
  private final String DEFAULT_LIMIT_STR = "100";
  private Credential cred;
  private VpcClient client;
}
