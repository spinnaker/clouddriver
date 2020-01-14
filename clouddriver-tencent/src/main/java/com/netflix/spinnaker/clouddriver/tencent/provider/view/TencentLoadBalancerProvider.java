package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.clouddriver.tencent.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancer;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerListener;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerRule;
import com.netflix.spinnaker.clouddriver.tencent.model.loadbalance.TencentLoadBalancerTarget;
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import groovy.lang.Closure;
import groovy.util.logging.Slf4j;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class TencentLoadBalancerProvider implements LoadBalancerProvider<TencentLoadBalancer> {
  @Autowired
  public TencentLoadBalancerProvider(Cache cacheView, TencentInfrastructureProvider tProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.tencentProvider = tProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    log.info("Enter tencent getApplicationLoadBalancers " + applicationName);

    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(LOAD_BALANCERS.ns));

    if (DefaultGroovyMethods.asBoolean(application)) {
      Collection<String> loadBalancerKeys = application.getRelationships().get(LOAD_BALANCERS.ns);
      if (DefaultGroovyMethods.asBoolean(loadBalancerKeys)) {
        Collection<CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);
        Set<TencentLoadBalancer> loadBalancerSet = translateLoadBalancersFromCacheData(loadBalancers);
        return ((Set<TencentLoadBalancer>) (loadBalancerSet));
      }

    } else {
      return null;
    }

    return null;
  }

  public Set<TencentLoadBalancer> getAll() {
    return getAllMatchingKeyPattern(Keys.getLoadBalancerKey("*", "*", "*"));
  }

  public Set<TencentLoadBalancer> getAllMatchingKeyPattern(String pattern) {
    log.info("Enter getAllMatchingKeyPattern patten = " + pattern);
    return loadResults(cacheView.filterIdentifiers(Keys.Namespace.LOAD_BALANCERS.ns, pattern));
  }

  public Set<TencentLoadBalancer> loadResults(Collection<String> identifiers) {
    log.info("Enter loadResults id = " + identifiers);
    Collection<CacheData> data = cacheView.getAll(Keys.Namespace.LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none());
    List<TencentLoadBalancer> transformed = DefaultGroovyMethods.collect(data, (Closure<TencentLoadBalancer>) new MethodClosure(this, "fromCacheData"));
    return ((Set<TencentLoadBalancer>) (transformed));
  }

  private Set<LoadBalancerInstance> getLoadBalancerInstanceByListenerId(TencentLoadBalancer loadBalancer, final String listenerId) {
    TencentLoadBalancerListener listener = DefaultGroovyMethods.find(loadBalancer.getListeners(), new Closure<Boolean>(this, this) {
      public Boolean doCall(TencentLoadBalancerListener it) {
        return it.getListenerId().equals(listenerId);
      }

      public Boolean doCall() {
        return doCall(null);
      }

    });

    final Set<LoadBalancerInstance> instances = (Set<LoadBalancerInstance>) new ArrayList();
    if (DefaultGroovyMethods.asBoolean(listener)) {
      DefaultGroovyMethods.each(listener.getTargets(), new Closure<Boolean>(this, this) {
        public Boolean doCall(TencentLoadBalancerTarget it) {
          LoadBalancerInstance instance1 = new LoadBalancerInstance();

          LoadBalancerInstance instance = instance1.setId(it.getInstanceId());
          return instances.add(instance);
        }

        public Boolean doCall() {
          return doCall(null);
        }

      });
      DefaultGroovyMethods.each(listener.getRules(), new Closure<List<TencentLoadBalancerTarget>>(this, this) {
        public List<TencentLoadBalancerTarget> doCall(Object rule) {
          return DefaultGroovyMethods.each(((TencentLoadBalancerRule) rule).getTargets(), new Closure<Boolean>(TencentLoadBalancerProvider.this, TencentLoadBalancerProvider.this) {
            public Boolean doCall(TencentLoadBalancerTarget it) {
              LoadBalancerInstance instance1 = new LoadBalancerInstance();

              LoadBalancerInstance instance = instance1.setId(it.getInstanceId());
              return instances.add(instance);
            }

            public Boolean doCall() {
              return doCall(null);
            }

          });
        }

      });
    }

    return instances;
  }

  private LoadBalancerServerGroup getLoadBalancerServerGroup(CacheData loadBalancerCache, final TencentLoadBalancer loadBalancerDesc) {
    Collection<String> serverGroupKeys = loadBalancerCache.getRelationships().get(SERVER_GROUPS.ns);
    if (DefaultGroovyMethods.asBoolean(serverGroupKeys)) {
      final String serverGroupKey = DefaultGroovyMethods.getAt(serverGroupKeys, 0);
      if (StringGroovyMethods.asBoolean(serverGroupKey)) {
        log.info("loadBalancer " + loadBalancerDesc.getLoadBalancerId() + " bind serverGroup " + serverGroupKey);
        Map<String, String> parts = Keys.parse(serverGroupKey);
        LoadBalancerServerGroup group = new LoadBalancerServerGroup();


        LoadBalancerServerGroup lbServerGroup = group.setName(parts.name) group.setAccount(parts.account)
        group.setRegion(parts.region);
        CacheData serverGroup = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
        if (DefaultGroovyMethods.asBoolean(serverGroup)) {
          final Map<String, Object> attributes = (serverGroup == null ? null : serverGroup.getAttributes());
          Map asgInfo = DefaultGroovyMethods.asType((attributes == null ? null : attributes.asg), Map.class);
          List lbInfo = DefaultGroovyMethods.asType(asgInfo.get("forwardLoadBalancerSet"), List.class);
          if (DefaultGroovyMethods.asBoolean(lbInfo)) {
            //def lbId = lbInfo[0]["loadBalancerId"] as String
            final String listenerId = DefaultGroovyMethods.asType(DefaultGroovyMethods.getAt(lbInfo.get(0), "listenerId"), String.class);
            log.info("loadBalancer " + loadBalancerDesc.getLoadBalancerId() + " listener " + listenerId + " bind serverGroup " + serverGroupKey);
            if (StringGroovyMethods.size(listenerId) > 0) {
              lbServerGroup.setInstances(getLoadBalancerInstanceByListenerId(loadBalancerDesc, listenerId));
            }

          }

        }

        return ((LoadBalancerServerGroup) (lbServerGroup));
      }

    }

    return null;
  }

  public TencentLoadBalancer fromCacheData(CacheData cacheData) {
    //log.info("Enter formCacheDate data = $cacheData.attributes")
    TencentLoadBalancer loadBalancerDescription = objectMapper.convertValue(cacheData.getAttributes(), TencentLoadBalancer.class);
    LoadBalancerServerGroup serverGroup = getLoadBalancerServerGroup(cacheData, loadBalancerDescription);
    if (DefaultGroovyMethods.asBoolean(serverGroup)) {
      loadBalancerDescription.getServerGroups().add(serverGroup);
    }

    return loadBalancerDescription;
  }

  @Override
  public List<TencentLoadBalancerDetail> byAccountAndRegionAndName(final String account, final String region, final String id) {
    log.info("Get loadBalancer byAccountAndRegionAndName: account=" + account + ",region=" + region + ",id=" + id);
    String lbKey = Keys.getLoadBalancerKey(id, account, region);
    Collection<CacheData> lbCache = cacheView.getAll(LOAD_BALANCERS.ns, lbKey);

    List<TencentLoadBalancerDetail> lbDetails = DefaultGroovyMethods.collect(lbCache, new Closure<TencentLoadBalancerDetail>(this, this) {
      public TencentLoadBalancerDetail doCall(CacheData it) {
        TencentLoadBalancerDetail lbDetail = new TencentLoadBalancerDetail();
        lbDetail.setId(it.getAttributes().id);
        lbDetail.setName(it.getAttributes().name);
        lbDetail.setAccount(account);
        lbDetail.setRegion(region);
        lbDetail.setVpcId(it.getAttributes().vpcId);
        lbDetail.setSubnetId(it.getAttributes().subnetId);
        lbDetail.setLoadBalancerType(it.getAttributes().loadBalancerType);
        lbDetail.setCreateTime(it.getAttributes().createTime);
        lbDetail.setLoadBalacnerVips(it.getAttributes().loadBalacnerVips);
        lbDetail.setSecurityGroups(it.getAttributes().securityGroups);
        lbDetail.setListeners(it.getAttributes().listeners);
        return lbDetail;
      }

      public TencentLoadBalancerDetail doCall() {
        return doCall(null);
      }

    });
    return ((List<TencentLoadBalancerDetail>) (lbDetails));
  }

  @Override
  public List<Item> list() {
    log.info("Enter list loadBalancer");
    String searchKey = Keys.getLoadBalancerKey("*", "*", "*");
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey);
    return DefaultGroovyMethods.asType(getSummaryForLoadBalancers(identifiers).values(), List.class);
  }

  @Override
  public Item get(final String id) {
    log.info("Enter Get loadBalancer id " + id);
    String searchKey = Keys.getLoadBalancerKey(id, "*", "*");
    Collection<String> identifiers = DefaultGroovyMethods.findAll(cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey), new Closure<Boolean>(this, this) {
      public Boolean doCall(String it) {
        Map<String, String> key = Keys.parse(it);
        return key.id.equals(id);
      }

      public Boolean doCall() {
        return doCall(null);
      }

    });
    return getSummaryForLoadBalancers(identifiers).get(id);
  }

  private Map<String, TencentLoadBalancerSummary> getSummaryForLoadBalancers(Collection<String> loadBalancerKeys) {
    Map<String, TencentLoadBalancerSummary> map = new LinkedHashMap<String, TencentLoadBalancerSummary>();
    Collection<CacheData> loadBalancerData = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);
    Map<String, CacheData> loadBalancers = DefaultGroovyMethods.collectEntries(loadBalancerData, new Closure<LinkedHashMap<String, CacheData>>(this, this) {
      public LinkedHashMap<String, CacheData> doCall(CacheData it) {
        LinkedHashMap<String, CacheData> map = new LinkedHashMap<String, CacheData>(1);
        map.put(it.getId(), it);
        return map;
      }

      public LinkedHashMap<String, CacheData> doCall() {
        return doCall(null);
      }

    });

    for (String lb : loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers.get(lb);
      if (DefaultGroovyMethods.asBoolean(loadBalancerFromCache)) {
        Map<String, String> parts = Keys.parse(lb);
        String name = parts.id;//loadBalancerId
        String region = parts.region;
        String account = parts.account;
        TencentLoadBalancerSummary summary = ((LinkedHashMap<String, TencentLoadBalancerSummary>) map).get(name);
        if (!DefaultGroovyMethods.asBoolean(summary)) {
          TencentLoadBalancerSummary summary1 = new TencentLoadBalancerSummary();

          summary = summary1.setName(name);
          ((LinkedHashMap<String, TencentLoadBalancerSummary>) map).put(name, summary);
        }

        TencentLoadBalancerDetail loadBalancer = new TencentLoadBalancerDetail();
        loadBalancer.setAccount(parts.account);
        loadBalancer.setRegion(parts.region);
        loadBalancer.setId(parts.id);
        loadBalancer.setVpcId(loadBalancerFromCache.getAttributes().vpcId);
        loadBalancer.setName(loadBalancerFromCache.getAttributes().name);

        DefaultGroovyMethods.leftShift(summary.getOrCreateAccount(account).getOrCreateRegion(region).getLoadBalancers(), loadBalancer);
      }

    }

    return map;
  }

  private Set<TencentLoadBalancer> translateLoadBalancersFromCacheData(Collection<CacheData> loadBalancerData) {

    List<TencentLoadBalancer> transformed = DefaultGroovyMethods.collect(loadBalancerData, (Closure<TencentLoadBalancer>) new MethodClosure(this, "fromCacheData"));
    return ((Set<TencentLoadBalancer>) (transformed));

/*
    Set<TencentLoadBalancer> loadBalancers = loadBalancerData.collect {
      def loadBalancer = new TencentLoadBalancer()
      loadBalancer.accountName = it.attributes.accountName
      loadBalancer.name = it.attributes.name
      loadBalancer.region = it.attributes.region
      loadBalancer.id = it.attributes.id
      loadBalancer.application = it.attributes.application
      loadBalancer.loadBalancerId = it.attributes.loadBalancerId
      loadBalancer.loadBalancerName = it.attributes.loadBalancerName
      loadBalancer.vpcId = it.attributes.vpcId
      //listeners
      loadBalancer.listeners = it.attributes.listeners.collect { listenerEntry ->
        def listener = new TencentLoadBalancerListener()
        listener.listenerId = listenerEntry.listenerId
        listener.listenerName = listenerEntry.listenerName
        listener.protocol = listenerEntry.protocol
        listener.port = listenerEntry.port
        listener.sessionExpireTime = listenerEntry.sessionExpireTime
        listener.scheduler = listenerEntry.scheduler
        listener.sniSwitch = listenerEntry.sniSwitch
        //rules
        listener.rules = listenerEntry.rules.collect { ruleEntry ->
          def rule = new TencentLoadBalancerRule()
          rule.locationId = ruleEntry.locationId
          rule.domain = ruleEntry.domain
          rule.url = ruleEntry.url
          rule.scheduler = ruleEntry.scheduler
          rule.sessionExpireTime = ruleEntry.sessionExpireTime
          //ruleTargets
          rule.targets = ruleEntry.targets.collect { ruleTargetEntry ->
            def ruleTarget = new TencentLoadBalancerTarget()
            ruleTarget.instanceId = ruleTargetEntry.instanceId
            ruleTarget.port = ruleTargetEntry.port
            ruleTarget.weight = ruleTargetEntry.weight
            ruleTarget.type = ruleTargetEntry.type
            ruleTarget
          }
          rule
        }
        //targets
        listener.targets = listenerEntry.targets.collect { targetEntry ->
          def target = new TencentLoadBalancerTarget()
          target.instanceId = targetEntry.instanceId
          target.port = targetEntry.port
          target.weight = targetEntry.weight
          target.type = targetEntry.type
          target
        }
        listener
      } //end listener

      loadBalancer
    }
    return loadBalancers
    */
  }

  public final String getCloudProvider() {
    return cloudProvider;
  }

  public final ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  private final String cloudProvider = TencentCloudProvider.ID;
  private final Cache cacheView;
  private final ObjectMapper objectMapper;
  private final TencentInfrastructureProvider tencentProvider;

  public static class TencentLoadBalancerSummary implements Item {
    public TencentLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        TencentLoadBalancerAccount account = new TencentLoadBalancerAccount();

        mappedAccounts.put(name, account.setName(name));
      }

      return ((TencentLoadBalancerAccount) (mappedAccounts.get(name)));
    }

    @JsonProperty("accounts")
    public List<TencentLoadBalancerAccount> getByAccounts() {
      return DefaultGroovyMethods.asType(mappedAccounts.values(), List.class);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    private Map<String, TencentLoadBalancerAccount> mappedAccounts = new LinkedHashMap<String, TencentLoadBalancerAccount>();
    private String name;
  }

  public static class TencentLoadBalancerAccount implements ByAccount {
    public TencentLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        TencentLoadBalancerAccountRegion region = new TencentLoadBalancerAccountRegion();


        mappedRegions.put(name, region.setName(name)region.setLoadBalancers(new ArrayList()));
      }

      return ((TencentLoadBalancerAccountRegion) (mappedRegions.get(name)));
    }

    @JsonProperty("regions")
    public List<TencentLoadBalancerAccountRegion> getByRegions() {
      return DefaultGroovyMethods.asType(mappedRegions.values(), List.class);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    private Map<String, TencentLoadBalancerAccountRegion> mappedRegions = new LinkedHashMap<String, TencentLoadBalancerAccountRegion>();
    private String name;
  }

  public static class TencentLoadBalancerAccountRegion implements ByRegion {
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<TencentLoadBalancerSummary> getLoadBalancers() {
      return loadBalancers;
    }

    public void setLoadBalancers(List<TencentLoadBalancerSummary> loadBalancers) {
      this.loadBalancers = loadBalancers;
    }

    private String name;
    private List<TencentLoadBalancerSummary> loadBalancers;
  }

  public static class TencentLoadBalancerDetail implements Details {
    public String getAccount() {
      return account;
    }

    public void setAccount(String account) {
      this.account = account;
    }

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getLoadBalancerType() {
      return loadBalancerType;
    }

    public void setLoadBalancerType(String loadBalancerType) {
      this.loadBalancerType = loadBalancerType;
    }

    public Integer getForwardType() {
      return forwardType;
    }

    public void setForwardType(Integer forwardType) {
      this.forwardType = forwardType;
    }

    public String getVpcId() {
      return vpcId;
    }

    public void setVpcId(String vpcId) {
      this.vpcId = vpcId;
    }

    public String getSubnetId() {
      return subnetId;
    }

    public void setSubnetId(String subnetId) {
      this.subnetId = subnetId;
    }

    public Integer getProjectId() {
      return projectId;
    }

    public void setProjectId(Integer projectId) {
      this.projectId = projectId;
    }

    public String getCreateTime() {
      return createTime;
    }

    public void setCreateTime(String createTime) {
      this.createTime = createTime;
    }

    public List<String> getLoadBalacnerVips() {
      return loadBalacnerVips;
    }

    public void setLoadBalacnerVips(List<String> loadBalacnerVips) {
      this.loadBalacnerVips = loadBalacnerVips;
    }

    public List<String> getSecurityGroups() {
      return securityGroups;
    }

    public void setSecurityGroups(List<String> securityGroups) {
      this.securityGroups = securityGroups;
    }

    public List<TencentLoadBalancerListener> getListeners() {
      return listeners;
    }

    public void setListeners(List<TencentLoadBalancerListener> listeners) {
      this.listeners = listeners;
    }

    private String account;
    private String region;
    private String name;
    private String id;
    private String type = TencentCloudProvider.ID;
    private String loadBalancerType;
    private Integer forwardType;
    private String vpcId;
    private String subnetId;
    private Integer projectId;
    private String createTime;
    private List<String> loadBalacnerVips;
    private List<String> securityGroups;
    private List<TencentLoadBalancerListener> listeners;
  }
}
