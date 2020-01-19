package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentCluster;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentInstance;
import com.netflix.spinnaker.clouddriver.tencent.model.TencentServerGroup;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Slf4j
@Data
@Component
public class TencentClusterProvider implements ClusterProvider<TencentCluster> {
  @Override
  public Map<String, Set<TencentCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns);
    Collection<TencentCluster> clusters = translateClusters(clusterData, false);
    return clusters.stream()
        .collect(Collectors.groupingBy(TencentCluster::getAccountName, Collectors.toSet()));
  }

  @Override
  public Map<String, Set<TencentCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    if (application != null) {
      Collection<TencentCluster> clusters =
          translateClusters(resolveRelationshipData(application, CLUSTERS.ns), false);
      return clusters.stream()
          .collect(Collectors.groupingBy(TencentCluster::getAccountName, Collectors.toSet()));
    } else {
      return null;
    }
  }

  @Override
  public Map<String, Set<TencentCluster>> getClusterDetails(String applicationName) {
    final CacheData application =
        cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    if (application != null) {
      Collection<TencentCluster> clusters =
          translateClusters(resolveRelationshipData(application, CLUSTERS.ns), true);
      return clusters.stream()
          .collect(Collectors.groupingBy(TencentCluster::getAccountName, Collectors.toSet()));
    } else {
      return null;
    }
  }

  @Override
  public Set<TencentCluster> getClusters(String applicationName, final String account) {
    CacheData application =
        cacheView.get(
            APPLICATIONS.ns,
            Keys.getApplicationKey(applicationName),
            RelationshipCacheFilter.include(CLUSTERS.ns));

    log.info(
        "TencentClusterProvider getClusters with parameters: appname {}, account {}, app: {} ",
        applicationName,
        account,
        application);
    if (application != null) {
      Collection<String> clusterKeys =
          application.getRelationships().get(CLUSTERS.ns).stream()
              .filter(
                  it -> {
                    return Keys.parse(it).get("account").equals(account);
                  })
              .collect(Collectors.toList());
      Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys);
      return (Set<TencentCluster>) translateClusters(clusters, true);
    } else {
      return null;
    }
  }

  @Override
  public TencentCluster getCluster(
      String application, String account, String name, boolean includeDetails) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account));
    return cluster != null
        ? translateClusters(new ArrayList<CacheData>(Arrays.asList(cluster)), includeDetails)
            .iterator()
            .next()
        : null;
  }

  @Override
  public TencentCluster getCluster(String applicationName, String accountName, String clusterName) {
    return getCluster(applicationName, accountName, clusterName, true);
  }

  @Override
  public TencentServerGroup getServerGroup(
      String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
    if (serverGroupData != null) {
      String imageId =
          (String)
              ((Map<String, Object>) serverGroupData.getAttributes().get("launchConfig"))
                  .get("imageId");
      CacheData imageConfig =
          !StringUtils.isEmpty(imageId)
              ? cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region))
              : null;

      log.info("TencentClusterProvider getServerGroup account = {}", account);
      TencentServerGroup serverGroup = TencentServerGroup.builder().build();
      serverGroup.setAccountName(account);
      serverGroup.setImage(
          imageConfig != null
              ? (Map<String, Object>) imageConfig.getAttributes().get("image")
              : null);

      if (includeDetails) {
        // show instances info
        serverGroup.setInstances(getServerGroupInstances(account, region, serverGroupData));
      }

      return serverGroup;
    } else {
      return null;
    }
  }

  @Override
  public TencentServerGroup getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true);
  }

  @Override
  public String getCloudProviderId() {
    return tencentCloudProvider.getId();
  }

  @Override
  public boolean supportsMinimalClusters() {
    return true;
  }

  public String getServerGroupAsgId(String serverGroupName, String account, String region) {
    TencentServerGroup serverGroup = getServerGroup(account, region, serverGroupName, false);
    return serverGroup != null ? (String) serverGroup.getAsg().get("autoScalingGroupId") : null;
  }

  private Collection<TencentCluster> translateClusters(
      Collection<CacheData> clusterData, final boolean includeDetails) {

    // todo test lb detail
    final Map<String, TencentLoadBalancer> loadBalancers = new HashMap<>();
    Map<String, TencentServerGroup> serverGroups;

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers =
          resolveRelationshipDataForCollection(clusterData, LOAD_BALANCERS.ns);
      Collection<CacheData> allServerGroups =
          resolveRelationshipDataForCollection(
              clusterData,
              SERVER_GROUPS.ns,
              RelationshipCacheFilter.include(INSTANCES.ns, LAUNCH_CONFIGS.ns));
      loadBalancers.putAll(translateLoadBalancers(allLoadBalancers));
      serverGroups = translateServerGroups(allServerGroups);
    } else {
      Collection<CacheData> allServerGroups =
          resolveRelationshipDataForCollection(
              clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns));
      serverGroups = translateServerGroups(allServerGroups);
    }

    Collection<TencentCluster> clusters =
        clusterData.stream()
            .map(
                clusterDataEntry -> {
                  Map<String, String> clusterKey = Keys.parse(clusterDataEntry.getId());
                  TencentCluster cluster = TencentCluster.builder().build();
                  cluster.setAccountName(clusterKey.get("account"));
                  cluster.setName(clusterKey.get("cluster"));
                  cluster.setServerGroups(
                      clusterDataEntry.getRelationships()
                          .getOrDefault(SERVER_GROUPS.ns, new ArrayList<>()).stream()
                          .map(
                              it -> {
                                return serverGroups.get(it);
                              })
                          .filter(it -> it != null)
                          .collect(Collectors.toSet()));

                  if (includeDetails) {
                    Set<TencentLoadBalancer> lb =
                        clusterDataEntry.getRelationships()
                            .getOrDefault(LOAD_BALANCERS.ns, new ArrayList<>()).stream()
                            .map(
                                it -> {
                                  return loadBalancers.get(it);
                                })
                            .filter(it -> it != null)
                            .collect(Collectors.toSet());
                    cluster.setLoadBalancers(lb);
                  } else {
                    cluster.setLoadBalancers(
                        clusterDataEntry.getRelationships().get(LOAD_BALANCERS.ns).stream()
                            .map(
                                loadBalancerKey -> {
                                  Map parts = Keys.parse(loadBalancerKey);
                                  TencentLoadBalancer balancer =
                                      TencentLoadBalancer.builder()
                                          .id((String) parts.get("id"))
                                          .accountName((String) parts.get("account"))
                                          .region((String) parts.get("region"))
                                          .build();
                                  return balancer;
                                })
                            .collect(Collectors.toSet()));
                  }
                  return cluster;
                })
            .collect(Collectors.toList());
    log.info("tencentClusterProvider translateCluster clusters {}", clusters);
    return clusters;
  }

  private static Map<String, TencentLoadBalancer> translateLoadBalancers(
      Collection<CacheData> loadBalancerData) {
    return loadBalancerData.stream()
        .map(
            it -> {
              Map<String, String> lbKey = Keys.parse(it.getId());
              return TencentLoadBalancer.builder()
                  .id(lbKey.get("id"))
                  .accountName(lbKey.get("account"))
                  .region(lbKey.get("region"))
                  .build();
            })
        .collect(Collectors.toMap(TencentLoadBalancer::getId, v -> v));
  }

  private Map<String, TencentServerGroup> translateServerGroups(
      Collection<CacheData> serverGroupData) {
    Map<String, TencentServerGroup> serverGroups = new HashMap<>();
    serverGroupData.stream()
        .forEach(
            it -> {
              Map<String, Object> attributes = it.getAttributes();
              log.info("TencentClusterProvider translateServerGroup {}", attributes);
              TencentServerGroup serverGroup =
                  TencentServerGroup.builder()
                      .name((String) attributes.get("name"))
                      .accountName((String) attributes.get("accountName"))
                      .region((String) attributes.get("region"))
                      .asg((Map<String, Object>) attributes.get("asg"))
                      .zones((Set<String>) attributes.get("zoneSet"))
                      .scheduledActions((List<Map>) attributes.get("scheduledActions"))
                      .scalingPolicies((List<Map>) attributes.get("scalingPolicies"))
                      .build();
              String account = serverGroup.getAccountName();
              String region = serverGroup.getRegion();
              serverGroup.setInstances(getServerGroupInstances(account, region, it));
              String imageId =
                  (String)
                      ((Map<String, Object>) it.getAttributes().get("launchConfig")).get("imageId");
              CacheData imageConfig =
                  !StringUtils.isEmpty(imageId)
                      ? getCacheView().get(IMAGES.ns, Keys.getImageKey(imageId, account, region))
                      : null;

              serverGroup.setImage(
                  imageConfig != null ? (Map) imageConfig.getAttributes().get("image") : null);
              serverGroups.put(it.getId(), serverGroup);
            });
    return serverGroups;
  }

  private Set<TencentInstance> getServerGroupInstances(
      final String account, final String region, CacheData serverGroupData) {
    Collection<String> instanceKeys = serverGroupData.getRelationships().get(INSTANCES.ns);
    Collection<CacheData> instances = cacheView.getAll(INSTANCES.ns, instanceKeys);
    log.info("tencentClusterProvider getServerGroupInstances {}", instances);

    return instances.stream()
        .map(
            it -> {
              return getTencentInstanceProvider().instanceFromCacheData(account, region, it);
            })
        .collect(Collectors.toSet());
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    return resolveRelationshipData(source, relationship, it -> true);
  }

  private Collection<CacheData> resolveRelationshipData(
      CacheData source, String relationship, Predicate<String> relFilter, CacheFilter cacheFilter) {
    if (CollectionUtils.isEmpty(source.getRelationships())
        || !source.getRelationships().containsKey(relationship)) {
      return new ArrayList<>();
    }
    Collection<String> filteredRelationships =
        source.getRelationships().get(relationship).stream()
            .filter(relFilter)
            .collect(Collectors.toList());
    return !CollectionUtils.isEmpty(filteredRelationships)
        ? cacheView.getAll(relationship, filteredRelationships, cacheFilter)
        : new ArrayList();
  }

  private Collection<CacheData> resolveRelationshipData(
      CacheData source, String relationship, Predicate<String> relFilter) {
    return resolveRelationshipData(source, relationship, relFilter, null);
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(
      Collection<CacheData> sources, String relationship, CacheFilter cacheFilter) {

    final List<String> relationships = new ArrayList<>();
    sources.stream()
        .forEach(
            it -> {
              final Collection<String> strings = it.getRelationships().get(relationship);
              relationships.addAll(strings);
            });
    return CollectionUtils.isEmpty(relationships)
        ? new ArrayList<>()
        : cacheView.getAll(relationship, relationships, cacheFilter);
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(
      Collection<CacheData> sources, String relationship) {
    return resolveRelationshipDataForCollection(sources, relationship, null);
  }

  public TencentCloudProvider getTencentCloudProvider() {
    return tencentCloudProvider;
  }

  public void setTencentCloudProvider(TencentCloudProvider tencentCloudProvider) {
    this.tencentCloudProvider = tencentCloudProvider;
  }

  public TencentInstanceProvider getTencentInstanceProvider() {
    return tencentInstanceProvider;
  }

  public void setTencentInstanceProvider(TencentInstanceProvider tencentInstanceProvider) {
    this.tencentInstanceProvider = tencentInstanceProvider;
  }

  public Cache getCacheView() {
    return cacheView;
  }

  public void setCacheView(Cache cacheView) {
    this.cacheView = cacheView;
  }

  @Autowired private TencentCloudProvider tencentCloudProvider;
  @Autowired private TencentInstanceProvider tencentInstanceProvider;
  @Autowired private Cache cacheView;
}
