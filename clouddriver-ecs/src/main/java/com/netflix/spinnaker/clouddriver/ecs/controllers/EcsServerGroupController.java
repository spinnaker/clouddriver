package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/applications/{application}/{account}/{serverGroupName}")
public class EcsServerGroupController {

  private final AccountCredentialsProvider accountCredentialsProvider;

  private final AmazonClientProvider amazonClientProvider;

  private final ServiceCacheClient serviceCacheClient;

  @Autowired
  public EcsServerGroupController(AccountCredentialsProvider accountCredentialsProvider, AmazonClientProvider amazonClientProvider, ServiceCacheClient serviceCacheClient) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.amazonClientProvider = amazonClientProvider;
    this.serviceCacheClient = serviceCacheClient;
  }

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  ResponseEntity getScalingActivities(@PathVariable String account, @PathVariable String serverGroupName, @RequestParam(value = "region", required = true) String region) {
    NetflixAmazonCredentials credentials = (NetflixAmazonCredentials) accountCredentialsProvider.getCredentials(account);

    if (!(credentials instanceof NetflixECSCredentials)) {
      return new ResponseEntity(String.format("Account %s is not an ECS account", account), HttpStatus.BAD_REQUEST);
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(credentials, region, true);

    Service cachedService = serviceCacheClient.getAll(account, region).stream()
      .filter(service -> service.getServiceName().equals(serverGroupName))
      .findFirst()
      .get();

    DescribeServicesResult describeServicesResult = ecs.describeServices(
      new DescribeServicesRequest()
        .withServices(serverGroupName)
        .withCluster(cachedService.getClusterArn())
    );

    return new ResponseEntity(describeServicesResult.getServices().get(0).getEvents(), HttpStatus.OK);
  }
}
