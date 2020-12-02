/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Cluster;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsClusterCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsClusterProvider {

  private EcsClusterCacheClient ecsClusterCacheClient;
  @Autowired private CredentialsRepository<NetflixECSCredentials> credentialsRepository;
  @Autowired private AmazonClientProvider amazonClientProvider;
  private static final int EcsClusterDescriptionMaxSize = 100;

  @Autowired
  public EcsClusterProvider(Cache cacheView) {
    this.ecsClusterCacheClient = new EcsClusterCacheClient(cacheView);
  }

  public Collection<EcsCluster> getAllEcsClusters() {
    return ecsClusterCacheClient.getAll();
  }

  public Collection<Cluster> getAllEcsClustersDescription(String account, String region) {
    List<String> clusterNames = new ArrayList<>();
    List<Cluster> clusters = new ArrayList<>();

    Collection<EcsCluster> ecsClusters = ecsClusterCacheClient.getAll();
    AmazonECS client = getAmazonEcsClient(account, region);
    if (ecsClusters.size() > 0 && ecsClusters != null) {
      for (EcsCluster ecsCluster : ecsClusters) {
        clusterNames.add(ecsCluster.getName());
        if (clusterNames.size() % EcsClusterDescriptionMaxSize == 0) {
          clusters = getDescribeClusters(client, clusterNames, clusters);
          clusterNames.clear();
        }
      }
      if (clusterNames.size() % EcsClusterDescriptionMaxSize != 0) {
        clusters = getDescribeClusters(client, clusterNames, clusters);
      }
    }
    return clusters;
  }

  private AmazonECS getAmazonEcsClient(String account, String region) {
    NetflixECSCredentials credentials = credentialsRepository.getOne(account);
    if (!(credentials instanceof NetflixECSCredentials)) {
      throw new IllegalArgumentException("Invalid credentials:" + account + ":" + region);
    }
    return amazonClientProvider.getAmazonEcs(credentials, region, true);
  }

  private List<Cluster> getDescribeClusters(
      AmazonECS client, List<String> clusterNames, List<Cluster> clusters) {
    DescribeClustersRequest describeClustersRequest =
        new DescribeClustersRequest().withClusters(clusterNames);
    DescribeClustersResult describeClustersResult =
        client.describeClusters(describeClustersRequest);
    if (describeClustersResult.getClusters().size() > 0) {
      for (Cluster cluster : describeClustersResult.getClusters()) {
        clusters.add(cluster);
      }
    }
    return clusters;
  }
}
