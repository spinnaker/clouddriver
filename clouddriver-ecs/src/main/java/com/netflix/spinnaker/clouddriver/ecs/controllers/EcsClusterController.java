/*
 *
 *  * Copyright 2017 Lookout, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class EcsClusterController {

  AmazonClientProvider amazonClientProvider;

  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcsClusterController(AmazonClientProvider amazonClientProvider,
                              AccountCredentialsProvider accountCredentialsProvider) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  private NetflixAmazonCredentials getCredentials(String account) {
    AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(account);

    if (accountCredentials instanceof NetflixAmazonCredentials) {
      return (NetflixAmazonCredentials) accountCredentials;
    }

    throw new NotFoundException(String.format("AWS account %s was not found.  Please specify a valid account name"));
  }

  @RequestMapping(value = "/ecs/{account}/{region}/ecscluster", method = RequestMethod.GET)
  public List<String> findEcsClusters(@PathVariable("account") String account,
                                      @PathVariable("region") String region) {

    AmazonECS amazonECS = amazonClientProvider.getAmazonEcs(account, getCredentials(account).getCredentialsProvider(), region);

    ListClustersRequest listClustersRequest = new ListClustersRequest();
    ListClustersResult listClustersResult = amazonECS.listClusters(listClustersRequest);

    List<String> listCluster = new ArrayList<>();

    for (String clusterArn: listClustersResult.getClusterArns()) {
      String ecsClusterName = inferClusterNameFromClusterArn(clusterArn);
      listCluster.add(ecsClusterName);
    }

    return listCluster;
  }

  private String inferClusterNameFromClusterArn(String clusterArn) {
    return clusterArn.split("/")[1];
  }

}
