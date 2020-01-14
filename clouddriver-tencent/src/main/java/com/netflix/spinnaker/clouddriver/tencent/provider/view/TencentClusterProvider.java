package com.netflix.spinnaker.clouddriver.tencent.provider.view;

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
import groovy.lang.Closure;
import groovy.lang.Reference;
import groovy.util.logging.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;

@Slf4j
@Component
public class TencentClusterProvider implements ClusterProvider<TencentCluster> {
  @Override
  public Map<String, Set<TencentCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns);
    Collection<TencentCluster> clusters = translateClusters(clusterData, false);
    return DefaultGroovyMethods.asType(DefaultGroovyMethods.collectEntries(DefaultGroovyMethods.groupBy(clusters, new Closure<String>(this, this) {
      public String doCall(TencentCluster it) {
        return it.getAccountName();
      }

      public String doCall() {
        return doCall(null);
      }

    }), new Closure<List<Serializable>>(this, this) {
      public List<Serializable> doCall(Object k, Object v) {
        return new ArrayList<Serializable>(Arrays.asList(k, new HashSet((Collection) v)));
      }

    }), Map.class);
  }

  @Override
  public Map<String, Set<TencentCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    if (DefaultGroovyMethods.asBoolean(application)) {
      Collection<TencentCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), false);
      return DefaultGroovyMethods.asType(DefaultGroovyMethods.collectEntries(DefaultGroovyMethods.groupBy(clusters, new Closure<String>(this, this) {
        public String doCall(TencentCluster it) {
          return it.getAccountName();
        }

        public String doCall() {
          return doCall(null);
        }

      }), new Closure<List<Serializable>>(this, this) {
        public List<Serializable> doCall(Object k, Object v) {
          return new ArrayList<Serializable>(Arrays.asList(k, new HashSet((Collection) v)));
        }

      }), Map.class);
    } else {
      return null;
    }

  }

  @Override
  public Map<String, Set<TencentCluster>> getClusterDetails(String applicationName) {
    final CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName));

    if (DefaultGroovyMethods.asBoolean(application)) {
      log.info("application is " + application.getId() + ".");
      Collection<TencentCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), true);
      return DefaultGroovyMethods.asType(DefaultGroovyMethods.collectEntries(DefaultGroovyMethods.groupBy(clusters, new Closure<String>(this, this) {
        public String doCall(TencentCluster it) {
          return it.getAccountName();
        }

        public String doCall() {
          return doCall(null);
        }

      }), new Closure<List<Serializable>>(this, this) {
        public List<Serializable> doCall(Object k, Object v) {
          return new ArrayList<Serializable>(Arrays.asList(k, new HashSet((Collection) v)));
        }

      }), Map.class);
    } else {
      log.info("application is not found.");
      return null;
    }

  }

  @Override
  public Set<TencentCluster> getClusters(String applicationName, final String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(CLUSTERS.ns));

    if (DefaultGroovyMethods.asBoolean(application)) {
      Collection<String> clusterKeys = DefaultGroovyMethods.findAll(application.getRelationships().get(CLUSTERS.ns), new Closure<Boolean>(this, this) {
        public Boolean doCall(String it) {
          return Keys.parse(it).account.equals(account);
        }

        public Boolean doCall() {
          return doCall(null);
        }

      });
      Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys);
      return DefaultGroovyMethods.asType(translateClusters(clusters, true), Set.class);
    } else {
      return null;
    }

  }

  @Override
  public TencentCluster getCluster(String application, String account, String name, boolean includeDetails) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account));

    return DefaultGroovyMethods.asBoolean(cluster) ? DefaultGroovyMethods.getAt(translateClusters(new ArrayList<CacheData>(Arrays.asList(cluster)), includeDetails), 0) : null;
  }

  @Override
  public TencentCluster getCluster(String applicationName, String accountName, String clusterName) {
    return getCluster(applicationName, accountName, clusterName, true);
  }

  @Override
  public TencentServerGroup getServerGroup(String account, String region, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region);
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
    if (DefaultGroovyMethods.asBoolean(serverGroupData)) {
      String imageId = (String) DefaultGroovyMethods.getAt(serverGroupData.getAttributes().launchConfig, "imageId");
      CacheData imageConfig = StringGroovyMethods.asBoolean(imageId) ? cacheView.get(IMAGES.ns, Keys.getImageKey(imageId, account, region)) : null;


      TencentServerGroup serverGroup = new TencentServerGroup();
      serverGroup.setAccountName(account);
      serverGroup.setImage(DefaultGroovyMethods.asBoolean(imageConfig) ? DefaultGroovyMethods.asType(imageConfig.getAttributes().image, Map.class) : null);

      if (includeDetails) {
        // show instances info
        serverGroup.setInstances(getServerGroupInstances(account, region, serverGroupData));
      }

      return ((TencentServerGroup) (serverGroup));
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
    return DefaultGroovyMethods.asBoolean(serverGroup) ? DefaultGroovyMethods.asType(serverGroup.getAsg().autoScalingGroupId, String.class) : null;
  }

  private Collection<TencentCluster> translateClusters(Collection<CacheData> clusterData, final boolean includeDetails) {

    // todo test lb detail
    final Reference<Map<String, TencentLoadBalancer>> loadBalancers;
    final Reference<Map<String, TencentServerGroup>> serverGroups;

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(clusterData, LOAD_BALANCERS.ns);
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns, LAUNCH_CONFIGS.ns));
      loadBalancers.set(translateLoadBalancers(allLoadBalancers));
      serverGroups.set(translateServerGroups(allServerGroups));
    } else {
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns));
      serverGroups.set(translateServerGroups(allServerGroups));
    }


    Collection<TencentCluster> clusters = DefaultGroovyMethods.collect(clusterData, new Closure<TencentCluster>(this, this) {
      public TencentCluster doCall(CacheData clusterDataEntry) {
        Map<String, String> clusterKey = Keys.parse(clusterDataEntry.getId());
        TencentCluster cluster = new TencentCluster();
        cluster.setAccountName(clusterKey.account);
        cluster.setName(clusterKey.cluster);
        cluster.setServerGroups(DefaultGroovyMethods.findResults(clusterDataEntry.getRelationships().get(SERVER_GROUPS.ns), new Closure<TencentServerGroup>(TencentClusterProvider.this, TencentClusterProvider.this) {
          public TencentServerGroup doCall(String it) {
            return serverGroups.get().get(it);
          }

          public TencentServerGroup doCall() {
            return doCall(null);
          }

        }));

        if (includeDetails) {
          Collection<TencentLoadBalancer> lb = DefaultGroovyMethods.findResults(clusterDataEntry.getRelationships().get(LOAD_BALANCERS.ns), new Closure<TencentLoadBalancer>(TencentClusterProvider.this, TencentClusterProvider.this) {
            public TencentLoadBalancer doCall(String it) {
              return loadBalancers.get().get(it);
            }

            public TencentLoadBalancer doCall() {
              return doCall(null);
            }

          });
          cluster.setLoadBalancers(lb);
        } else {
          cluster.setLoadBalancers(DefaultGroovyMethods.collect(clusterDataEntry.getRelationships().get(LOAD_BALANCERS.ns), new Closure<TencentLoadBalancer>(TencentClusterProvider.this, TencentClusterProvider.this) {
            public TencentLoadBalancer doCall(Object loadBalancerKey) {
              Map parts = Keys.parse((String) loadBalancerKey);
              TencentLoadBalancer balancer = new TencentLoadBalancer();


              return balancer.setId(parts.id) balancer.setAccountName(parts.account) balancer.setRegion(parts.region);
            }

          }));
        }

        return cluster;
      }

    });
    return clusters;
  }

  private static Map<String, TencentLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    return DefaultGroovyMethods.collectEntries(loadBalancerData, new Closure<LinkedHashMap<String, TencentLoadBalancer>>(null, null) {
      public LinkedHashMap<String, TencentLoadBalancer> doCall(Object loadBalancerEntry) {
        Map<String, String> lbKey = Keys.parse(((CacheData) loadBalancerEntry).getId());
        LinkedHashMap<String, TencentLoadBalancer> map = new LinkedHashMap<String, TencentLoadBalancer>(1);
        TencentLoadBalancer balancer = new TencentLoadBalancer();


        map.put(((CacheData) loadBalancerEntry).getId(), balancer.setId(lbKey.id)balancer.setAccountName(lbKey.account)balancer.setRegion(lbKey.region));
        return map;
      }

    });
  }

  private Map<String, TencentServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Map<String, TencentServerGroup> serverGroups = DefaultGroovyMethods.collectEntries(serverGroupData, new Closure<LinkedHashMap<String, TencentServerGroup>>(this, this) {
      public LinkedHashMap<String, TencentServerGroup> doCall(Object serverGroupEntry) {
        TencentServerGroup serverGroup = new TencentServerGroup();

        String account = serverGroup.getAccountName();
        String region = serverGroup.getRegion();

        serverGroup.setInstances(getServerGroupInstances(account, region, (CacheData) serverGroupEntry));

        String imageId = (String) DefaultGroovyMethods.getAt(((CacheData) serverGroupEntry).getAttributes().launchConfig, "imageId");
        CacheData imageConfig = StringGroovyMethods.asBoolean(imageId) ? getCacheView().get(IMAGES.ns, Keys.getImageKey(imageId, account, region)) : null;

        serverGroup.setImage(DefaultGroovyMethods.asBoolean(imageConfig) ? DefaultGroovyMethods.asType(imageConfig.getAttributes().image, Map.class) : null);

        LinkedHashMap<String, TencentServerGroup> map = new LinkedHashMap<String, TencentServerGroup>(1);
        map.put(((CacheData) serverGroupEntry).getId(), serverGroup);
        return map;
      }

    });
    return serverGroups;
  }

  private Set<TencentInstance> getServerGroupInstances(final String account, final String region, CacheData serverGroupData) {
    Collection<String> instanceKeys = serverGroupData.getRelationships().get(INSTANCES.ns);
    Collection<CacheData> instances = cacheView.getAll(INSTANCES.ns, instanceKeys);

    return DefaultGroovyMethods.collect(instances, new Closure<TencentInstance>(this, this) {
      public TencentInstance doCall(CacheData it) {
        return getTencentInstanceProvider().instanceFromCacheData(account, region, it);
      }

      public TencentInstance doCall() {
        return doCall(null);
      }

    });
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    return resolveRelationshipData(source, relationship, new Closure<Boolean>(this, this) {
      public Boolean doCall(Object it) {
        return true;
      }

      public Boolean doCall() {
        return doCall(null);
      }

    });
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter, CacheFilter cacheFilter) {
    Collection<String> filteredRelationships = DefaultGroovyMethods.findAll(source.getRelationships().get(relationship), relFilter);
    return DefaultGroovyMethods.asBoolean(filteredRelationships) ? cacheView.getAll(relationship, filteredRelationships, cacheFilter) : new ArrayList();
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    return resolveRelationshipData(source, relationship, relFilter, null);
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter) {

    final Collection<?> flatten = DefaultGroovyMethods.flatten(DefaultGroovyMethods.findResults(sources, new Closure<Collection>(this, this) {
      public Collection doCall(CacheData it) {
        final Collection<String> strings = it.getRelationships().get(relationship);
        return DefaultGroovyMethods.asBoolean(strings) ? strings : new ArrayList();
      }

      public Collection doCall() {
        return doCall(null);
      }

    }));
    Collection<String> relationships = DefaultGroovyMethods.asBoolean(flatten) ? flatten : new ArrayList();

    return DefaultGroovyMethods.asBoolean(relationships) ? cacheView.getAll(relationship, relationships, cacheFilter) : new ArrayList();
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship) {
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

  @Autowired
  private TencentCloudProvider tencentCloudProvider;
  @Autowired
  private TencentInstanceProvider tencentInstanceProvider;
  @Autowired
  private Cache cacheView;
}
