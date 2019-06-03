package com.netflix.spinnaker.clouddriver.tencent.provider.view;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

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
import com.netflix.spinnaker.clouddriver.tencent.provider.TencentInfrastructureProvider;
import java.util.*;
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
public class TencentLoadBalancerProvider implements LoadBalancerProvider<TencentLoadBalancer> {
  @Autowired
  public TencentLoadBalancerProvider(
      Cache cacheView, TencentInfrastructureProvider tProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.tencentProvider = tProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public Set<TencentLoadBalancer> getApplicationLoadBalancers(String applicationName) {
    log.info("Enter tencent getApplicationLoadBalancers " + applicationName);

    CacheData application =
        cacheView.get(
            APPLICATIONS.ns,
            Keys.getApplicationKey(applicationName),
            RelationshipCacheFilter.include(LOAD_BALANCERS.ns));

    if (application != null) {
      Collection<String> loadBalancerKeys = application.getRelationships().get(LOAD_BALANCERS.ns);
      if (!CollectionUtils.isEmpty(loadBalancerKeys)) {
        Collection<CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);
        Set<TencentLoadBalancer> loadBalancerSet =
            translateLoadBalancersFromCacheData(loadBalancers);
        return loadBalancerSet;
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
    Collection<CacheData> data =
        cacheView.getAll(
            Keys.Namespace.LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.none());
    Set<TencentLoadBalancer> transformed =
        data.stream().map(it -> this.fromCacheData(it)).collect(Collectors.toSet());
    return transformed;
  }

  private Set<LoadBalancerInstance> getLoadBalancerInstanceByListenerId(
      TencentLoadBalancer loadBalancer, final String listenerId) {
    TencentLoadBalancerListener listener =
        loadBalancer.getListeners().stream()
            .filter(
                it -> {
                  return it.getListenerId().equals(listenerId);
                })
            .findFirst()
            .orElse(null);

    final Set<LoadBalancerInstance> instances = (Set<LoadBalancerInstance>) new ArrayList();
    if (listener != null) {
      listener
          .getTargets()
          .forEach(
              it -> {
                LoadBalancerInstance instance = new LoadBalancerInstance();
                instance.setId(it.getInstanceId());
                instances.add(instance);
              });
      listener
          .getRules()
          .forEach(
              rule -> {
                rule.getTargets()
                    .forEach(
                        target -> {
                          LoadBalancerInstance instance = new LoadBalancerInstance();
                          instance.setId(target.getInstanceId());
                          instances.add(instance);
                        });
              });
    }
    return instances;
  }

  private LoadBalancerServerGroup getLoadBalancerServerGroup(
      CacheData loadBalancerCache, final TencentLoadBalancer loadBalancerDesc) {
    Collection<String> serverGroupKeys = loadBalancerCache.getRelationships().get(SERVER_GROUPS.ns);
    if (!CollectionUtils.isEmpty(serverGroupKeys)) {
      final String serverGroupKey = serverGroupKeys.iterator().next();
      if (StringUtils.isEmpty(serverGroupKey)) {
        log.info(
            "loadBalancer "
                + loadBalancerDesc.getLoadBalancerId()
                + " bind serverGroup "
                + serverGroupKey);
        Map<String, String> parts = Keys.parse(serverGroupKey);
        LoadBalancerServerGroup lbServerGroup = new LoadBalancerServerGroup();
        lbServerGroup.setName(parts.get("name"));
        lbServerGroup.setAccount(parts.get("account"));
        lbServerGroup.setRegion(parts.get("region"));
        CacheData serverGroup = cacheView.get(SERVER_GROUPS.ns, serverGroupKey);
        if (serverGroup != null) {
          final Map<String, Object> attributes =
              (serverGroup == null ? null : serverGroup.getAttributes());
          Map asgInfo = attributes == null ? null : (Map) attributes.get("asg");
          List<Map<String, Object>> lbInfos =
              (List<Map<String, Object>>) asgInfo.get("forwardLoadBalancerSet");
          if (!CollectionUtils.isEmpty(lbInfos)) {
            Map<String, Object> lbInfo = lbInfos.get(0);
            // def lbId = lbInfo[0]["loadBalancerId"] as String
            final String listenerId = (String) lbInfo.get("listenerId");
            log.info(
                "loadBalancer "
                    + loadBalancerDesc.getLoadBalancerId()
                    + " listener "
                    + listenerId
                    + " bind serverGroup "
                    + serverGroupKey);
            if (!StringUtils.isEmpty(listenerId)) {
              lbServerGroup.setInstances(
                  getLoadBalancerInstanceByListenerId(loadBalancerDesc, listenerId));
            }
          }
        }
        return lbServerGroup;
      }
    }
    return null;
  }

  public TencentLoadBalancer fromCacheData(CacheData cacheData) {
    // log.info("Enter formCacheDate data = $cacheData.attributes")
    TencentLoadBalancer loadBalancerDescription =
        objectMapper.convertValue(cacheData.getAttributes(), TencentLoadBalancer.class);
    LoadBalancerServerGroup serverGroup =
        getLoadBalancerServerGroup(cacheData, loadBalancerDescription);
    if (serverGroup != null) {
      loadBalancerDescription.getServerGroups().add(serverGroup);
    }
    return loadBalancerDescription;
  }

  @Override
  public List<TencentLoadBalancerDetail> byAccountAndRegionAndName(
      final String account, final String region, final String id) {
    log.info(
        "Get loadBalancer byAccountAndRegionAndName: account="
            + account
            + ",region="
            + region
            + ",id="
            + id);
    String lbKey = Keys.getLoadBalancerKey(id, account, region);
    Collection<CacheData> lbCache = cacheView.getAll(LOAD_BALANCERS.ns, lbKey);

    List<TencentLoadBalancerDetail> lbDetails =
        lbCache.stream()
            .map(
                it -> {
                  TencentLoadBalancerDetail lbDetail = new TencentLoadBalancerDetail();
                  Map<String, Object> attributes = it.getAttributes();
                  lbDetail.setId((String) attributes.get("id"));
                  lbDetail.setName((String) attributes.get("name"));
                  lbDetail.setAccount(account);
                  lbDetail.setRegion(region);
                  lbDetail.setVpcId((String) attributes.get("vpcId"));
                  lbDetail.setSubnetId((String) attributes.get("subnetId"));
                  lbDetail.setLoadBalancerType((String) attributes.get("loadBalancerType"));
                  lbDetail.setCreateTime((String) attributes.get("createTime"));
                  lbDetail.setLoadBalacnerVips((List<String>) attributes.get("loadBalacnerVips"));
                  lbDetail.setSecurityGroups((List<String>) attributes.get("securityGroups"));
                  lbDetail.setListeners(
                      (List<TencentLoadBalancerListener>) attributes.get("listeners"));
                  return lbDetail;
                })
            .collect(Collectors.toList());
    return lbDetails;
  }

  @Override
  public List<Item> list() {
    log.info("Enter list loadBalancer");
    String searchKey = Keys.getLoadBalancerKey("*", "*", "*");
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey);
    return getSummaryForLoadBalancers(identifiers).values().stream().collect(Collectors.toList());
  }

  @Override
  public Item get(final String id) {
    log.info("Enter Get loadBalancer id " + id);
    String searchKey = Keys.getLoadBalancerKey(id, "*", "*");
    Collection<String> identifiers =
        cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey).stream()
            .filter(
                it -> {
                  Map<String, String> key = Keys.parse(it);
                  return key.get("id").equals(id);
                })
            .collect(Collectors.toList());
    return getSummaryForLoadBalancers(identifiers).get(id);
  }

  private Map<String, TencentLoadBalancerSummary> getSummaryForLoadBalancers(
      Collection<String> loadBalancerKeys) {
    Map<String, TencentLoadBalancerSummary> map = new HashMap<String, TencentLoadBalancerSummary>();
    Collection<CacheData> loadBalancerData = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys);
    Map<String, CacheData> loadBalancers =
        loadBalancerData.stream().collect(Collectors.toMap(CacheData::getId, data -> data));

    for (String lb : loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers.get(lb);
      if (loadBalancerFromCache != null) {
        Map<String, String> parts = Keys.parse(lb);
        String name = parts.get("id"); // loadBalancerId
        String region = parts.get("region");
        String account = parts.get("account");
        TencentLoadBalancerSummary summary = map.get(name);
        if (summary == null) {
          summary = new TencentLoadBalancerSummary();
          summary.setName(name);
          map.put(name, summary);
        }

        TencentLoadBalancerDetail loadBalancer = new TencentLoadBalancerDetail();
        loadBalancer.setAccount(parts.get("account"));
        loadBalancer.setRegion(parts.get("region"));
        loadBalancer.setId(parts.get("id"));
        loadBalancer.setVpcId((String) loadBalancerFromCache.getAttributes().get("vpcId"));
        loadBalancer.setName((String) loadBalancerFromCache.getAttributes().get("name"));

        summary
            .getOrCreateAccount(account)
            .getOrCreateRegion(region)
            .getLoadBalancers()
            .add(loadBalancer);
      }
    }
    return map;
  }

  private Set<TencentLoadBalancer> translateLoadBalancersFromCacheData(
      Collection<CacheData> loadBalancerData) {

    Set<TencentLoadBalancer> transformed =
        loadBalancerData.stream().map(it -> this.fromCacheData(it)).collect(Collectors.toSet());
    return transformed;

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
        account.setName(name);
        mappedAccounts.put(name, account);
      }
      return mappedAccounts.get(name);
    }

    @JsonProperty("accounts")
    public List<TencentLoadBalancerAccount> getByAccounts() {
      return mappedAccounts.values().stream().collect(Collectors.toList());
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    private Map<String, TencentLoadBalancerAccount> mappedAccounts =
        new LinkedHashMap<String, TencentLoadBalancerAccount>();
    private String name;
  }

  public static class TencentLoadBalancerAccount implements ByAccount {
    public TencentLoadBalancerAccountRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        TencentLoadBalancerAccountRegion region = new TencentLoadBalancerAccountRegion();
        region.setName(name);
        region.setLoadBalancers(new ArrayList());
        mappedRegions.put(name, region);
      }
      return mappedRegions.get(name);
    }

    @JsonProperty("regions")
    public List<TencentLoadBalancerAccountRegion> getByRegions() {
      return mappedRegions.values().stream().collect(Collectors.toList());
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    private Map<String, TencentLoadBalancerAccountRegion> mappedRegions =
        new LinkedHashMap<String, TencentLoadBalancerAccountRegion>();
    private String name;
  }

  public static class TencentLoadBalancerAccountRegion implements ByRegion {
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<TencentLoadBalancerDetail> getLoadBalancers() {
      return loadBalancers;
    }

    public void setLoadBalancers(List<TencentLoadBalancerDetail> loadBalancers) {
      this.loadBalancers = loadBalancers;
    }

    private String name;
    private List<TencentLoadBalancerDetail> loadBalancers;
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
