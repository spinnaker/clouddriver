package com.netflix.spinnaker.clouddriver.tencent.controllers;

import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tencent.client.AutoScalingClient;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentServerGroup;
import com.netflix.spinnaker.clouddriver.tencent.provider.view.TencentClusterProvider;
import com.netflix.spinnaker.clouddriver.tencent.security.TencentNamedAccountCredentials;
import com.tencentcloudapi.as.v20180419.models.Activity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/applications/{application}/clusters/{account}/{clusterName}/tencent/serverGroups/{serverGroupName}")
public class TencentServerGroupController {
  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  public ResponseEntity getScalingActivities(@PathVariable final String account, @PathVariable String serverGroupName, @RequestParam(value = "region", required = true) String region) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof TencentNamedAccountCredentials)) {
      return new ResponseEntity(new HashMap<String, String>() {{
        put("message", account + " is not tencent credential type");
      }}
        , HttpStatus.BAD_REQUEST);
    }

    TencentServerGroup serverGroup = tencentClusterProvider.getServerGroup(account, region, serverGroupName, false);
    String autoScalingGroupId = (String) serverGroup.getAsg().get("autoScalingGroupId");
    AutoScalingClient client = new AutoScalingClient(((TencentNamedAccountCredentials) credentials).getCredentials().getSecretId(),
      ((TencentNamedAccountCredentials) credentials).getCredentials().getSecretKey(),
      region);
    List<Activity> scalingActivities = client.getAutoScalingActivitiesByAsgId(autoScalingGroupId, MAX_SCALING_ACTIVITIES);
    return new ResponseEntity(scalingActivities, HttpStatus.OK);
  }

  public static int getMAX_SCALING_ACTIVITIES() {
    return MAX_SCALING_ACTIVITIES;
  }

  public AccountCredentialsProvider getAccountCredentialsProvider() {
    return accountCredentialsProvider;
  }

  public void setAccountCredentialsProvider(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  public TencentClusterProvider getTencentClusterProvider() {
    return tencentClusterProvider;
  }

  public void setTencentClusterProvider(TencentClusterProvider tencentClusterProvider) {
    this.tencentClusterProvider = tencentClusterProvider;
  }

  private static final int MAX_SCALING_ACTIVITIES = 500;
  @Autowired
  private AccountCredentialsProvider accountCredentialsProvider;
  @Autowired
  private TencentClusterProvider tencentClusterProvider;
}
