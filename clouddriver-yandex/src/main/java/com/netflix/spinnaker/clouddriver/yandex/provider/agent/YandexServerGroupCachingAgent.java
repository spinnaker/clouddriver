/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.provider.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup.*;
import static com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup.HealthCheckSpec;
import static com.netflix.spinnaker.clouddriver.yandex.provider.Keys.Namespace.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static yandex.cloud.api.compute.v1.InstanceServiceOuterClass.ListInstancesRequest;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.*;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.netflix.frigga.ami.AppVersion;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.yandex.CacheResultBuilder;
import com.netflix.spinnaker.clouddriver.yandex.YandexCloudProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudImage;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudInstance;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.Keys;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Value;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass;

@Getter
public final class YandexServerGroupCachingAgent extends AbstractYandexCachingAgent
    implements OnDemandAgent {
  private static final long GB = 1024 * 1024 * 1024;
  private static final String ON_DEMAND_TYPE =
      String.join(":", YandexCloudProvider.ID, OnDemandType.ServerGroup.getValue());
  private static final Splitter COMMA = Splitter.on(',').omitEmptyStrings().trimResults();
  private static final Splitter.MapSplitter IMAGE_DESCRIPTION_SPLITTER =
      Splitter.on(',').withKeyValueSeparator(": ");

  private Collection<AgentDataType> providedDataTypes =
      ImmutableSet.of(
          AUTHORITATIVE.forType(SERVER_GROUPS.getNs()),
          INFORMATIVE.forType(CLUSTERS.getNs()),
          INFORMATIVE.forType(LOAD_BALANCERS.getNs()));
  private String agentType = getAccountName() + "/" + getClass().getSimpleName();
  private String onDemandAgentType = getAgentType() + "-OnDemand";
  private OnDemandMetricsSupport metricsSupport;
  private final Namer<YandexCloudServerGroup> naming;

  public YandexServerGroupCachingAgent(
      YandexCloudCredentials credentials, Registry registry, ObjectMapper objectMapper) {
    super(credentials, objectMapper);
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, ON_DEMAND_TYPE);
    this.naming =
        NamerRegistry.lookup()
            .withProvider(YandexCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(YandexCloudServerGroup.class);
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(providedDataTypes);
    cacheResultBuilder.setStartTime(System.currentTimeMillis());

    List<YandexCloudServerGroup> serverGroups = getServerGroups(providerCache);

    // If an entry in ON_DEMAND was generated _after_ we started our caching run, add it to the
    // cacheResultBuilder, since we may use it in buildCacheResult.
    //
    // We don't evict things unless they've been processed because Orca, after sending an
    // on-demand cache refresh, doesn't consider the request "finished" until it calls
    // pendingOnDemandRequests and sees a processedCount of 1. In a saner world, Orca would
    // probably just trust that if the key wasn't returned by pendingOnDemandRequests, it must
    // have been processed. But we don't live in that world.
    Set<String> serverGroupKeys =
        serverGroups.stream()
            .map(
                serverGroup ->
                    Keys.getServerGroupKey(
                        getAccountName(), serverGroup.getId(), getFolder(), serverGroup.getName()))
            .collect(toSet());
    providerCache
        .getAll(Keys.Namespace.ON_DEMAND.getNs(), serverGroupKeys)
        .forEach(
            cacheData -> {
              long cacheTime = (long) cacheData.getAttributes().get("cacheTime");
              if (cacheTime < cacheResultBuilder.getStartTime()
                  && (int) cacheData.getAttributes().get("processedCount") > 0) {
                cacheResultBuilder.getOnDemand().getToEvict().add(cacheData.getId());
              } else {
                cacheResultBuilder.getOnDemand().getToKeep().put(cacheData.getId(), cacheData);
              }
            });

    CacheResult cacheResult = buildCacheResult(cacheResultBuilder, serverGroups);

    // For all the ON_DEMAND entries that we marked as 'toKeep' earlier, here we mark them as
    // processed so that they get evicted in future calls to this method. Why can't we just mark
    // them as evicted here, though? Why wait for another run?
    cacheResult
        .getCacheResults()
        .getOrDefault(Keys.Namespace.ON_DEMAND.getNs(), emptyList())
        .forEach(
            cacheData -> {
              cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
              int processedCount = (Integer) cacheData.getAttributes().get("processedCount");
              cacheData.getAttributes().put("processedCount", processedCount + 1);
            });

    return cacheResult;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandType.ServerGroup.equals(type) && YandexCloudProvider.ID.equals(cloudProvider);
  }

  @Nullable
  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {

    try {
      String serverGroupName = (String) data.get("serverGroupName");
      if (serverGroupName == null || !getAccountName().equals(data.get("account"))) {
        return null;
      }
      Optional<YandexCloudServerGroup> serverGroup =
          getMetricsSupport().readData(() -> getServerGroup(serverGroupName, providerCache));
      if (serverGroup.isPresent()) {
        CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();
        String serverGroupKey =
            Keys.getServerGroupKey(
                getAccountName(),
                serverGroup.get().getId(),
                getFolder(),
                serverGroup.get().getName());
        CacheResult result =
            getMetricsSupport()
                .transformData(
                    () ->
                        buildCacheResult(cacheResultBuilder, ImmutableList.of(serverGroup.get())));
        String cacheResults = getObjectMapper().writeValueAsString(result.getCacheResults());
        CacheData cacheData =
            getMetricsSupport()
                .onDemandStore(
                    () ->
                        new DefaultCacheData(
                            serverGroupKey,
                            /* ttlSeconds= */ (int) Duration.ofMinutes(10).getSeconds(),
                            ImmutableMap.of(
                                "cacheTime",
                                System.currentTimeMillis(),
                                "cacheResults",
                                cacheResults,
                                "processedCount",
                                0),
                            /* relationships= */ ImmutableMap.of()));
        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.getNs(), cacheData);
        return new OnDemandResult(
            getOnDemandAgentType(), result, /* evictions= */ ImmutableMap.of());
      } else {
        Collection<String> existingIdentifiers =
            ImmutableSet.of(
                Keys.getServerGroupKey(getAccountName(), "*", getFolder(), serverGroupName));
        providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.getNs(), existingIdentifiers);
        return new OnDemandResult(
            getOnDemandAgentType(),
            new DefaultCacheResult(ImmutableMap.of()),
            ImmutableMap.of(SERVER_GROUPS.getNs(), ImmutableList.copyOf(existingIdentifiers)));
      }
    } catch (IOException e) {
      // CatsOnDemandCacheUpdater handles this
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    List<String> ownedKeys =
        providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.getNs()).stream()
            .filter(this::keyOwnedByThisAgent)
            .collect(toImmutableList());

    return providerCache.getAll(Keys.Namespace.ON_DEMAND.getNs(), ownedKeys).stream()
        .map(
            cacheData -> {
              Map<String, Object> map = new HashMap<>();
              map.put("details", Keys.parse(cacheData.getId()));
              map.put("moniker", cacheData.getAttributes().get("moniker"));
              map.put("cacheTime", cacheData.getAttributes().get("cacheTime"));
              map.put("processedCount", cacheData.getAttributes().get("processedCount"));
              map.put("processedTime", cacheData.getAttributes().get("processedTime"));
              return map;
            })
        .collect(toImmutableList());
  }

  private boolean keyOwnedByThisAgent(String key) {
    Map<String, String> parsedKey = Keys.parse(key);
    return parsedKey != null && parsedKey.get("type").equals(SERVER_GROUPS.getNs());
  }

  private CacheResult buildCacheResult(
      CacheResultBuilder cacheResultBuilder, List<YandexCloudServerGroup> serverGroups) {
    try {
      for (YandexCloudServerGroup serverGroup : serverGroups) {

        Moniker moniker = naming.deriveMoniker(serverGroup);

        String applicationKey = Keys.getApplicationKey(moniker.getApp());
        String clusterKey =
            Keys.getClusterKey(getAccountName(), moniker.getApp(), moniker.getCluster());
        String serverGroupKey =
            Keys.getServerGroupKey(
                getAccountName(), serverGroup.getId(), getFolder(), serverGroup.getName());
        Set<String> instanceKeys =
            serverGroup.getInstances().stream()
                .map(
                    instance ->
                        Keys.getInstanceKey(
                            getAccountName(), instance.getId(), getFolder(), instance.getName()))
                .collect(toImmutableSet());

        CacheResultBuilder.CacheDataBuilder application =
            cacheResultBuilder.namespace(APPLICATIONS.getNs()).keep(applicationKey);
        application.getAttributes().put("name", moniker.getApp());
        application
            .getRelationships()
            .computeIfAbsent(CLUSTERS.getNs(), s -> new ArrayList<>())
            .add(clusterKey);
        application
            .getRelationships()
            .computeIfAbsent(INSTANCES.getNs(), s -> new ArrayList<>())
            .addAll(instanceKeys);

        CacheResultBuilder.CacheDataBuilder cluster =
            cacheResultBuilder.namespace(CLUSTERS.getNs()).keep(clusterKey);
        cluster.getAttributes().put("name", moniker.getCluster());
        cluster.getAttributes().put("accountName", getAccountName());
        cluster.getAttributes().put("moniker", moniker);
        cluster
            .getRelationships()
            .computeIfAbsent(APPLICATIONS.getNs(), s -> new ArrayList<>())
            .add(applicationKey);
        cluster
            .getRelationships()
            .computeIfAbsent(SERVER_GROUPS.getNs(), s -> new ArrayList<>())
            .add(serverGroupKey);
        cluster
            .getRelationships()
            .computeIfAbsent(INSTANCES.getNs(), s -> new ArrayList<>())
            .addAll(instanceKeys);

        Set<String> loadBalancerKeys =
            serverGroup.getLoadBalancerIntegration().getBalancers().stream()
                .map(
                    lb ->
                        Keys.getLoadBalancerKey(
                            getAccountName(), lb.getId(), getFolder(), lb.getName()))
                .collect(toSet());
        loadBalancerKeys.forEach(
            key ->
                cacheResultBuilder
                    .namespace(LOAD_BALANCERS.getNs())
                    .keep(key)
                    .getRelationships()
                    .computeIfAbsent(SERVER_GROUPS.getNs(), s -> new ArrayList<>())
                    .add(serverGroupKey));

        if (shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
          moveOnDemandDataToNamespace(cacheResultBuilder, serverGroupKey);
        } else {
          CacheResultBuilder.CacheDataBuilder serverGroupCacheData =
              cacheResultBuilder.namespace(SERVER_GROUPS.getNs()).keep(serverGroupKey);
          serverGroupCacheData.setAttributes(
              getObjectMapper()
                  .convertValue(serverGroup, new TypeReference<Map<String, Object>>() {}));
          serverGroupCacheData
              .getRelationships()
              .computeIfAbsent(APPLICATIONS.getNs(), s -> new ArrayList<>())
              .add(applicationKey);
          serverGroupCacheData
              .getRelationships()
              .computeIfAbsent(CLUSTERS.getNs(), s -> new ArrayList<>())
              .add(clusterKey);
          serverGroupCacheData
              .getRelationships()
              .computeIfAbsent(LOAD_BALANCERS.getNs(), s -> new ArrayList<>())
              .addAll(loadBalancerKeys);
          serverGroupCacheData
              .getRelationships()
              .computeIfAbsent(INSTANCES.getNs(), s -> new ArrayList<>())
              .addAll(instanceKeys);
        }
      }
    } catch (IOException e) {
      // CatsOnDemandCacheUpdater handles this
      throw new UncheckedIOException(e);
    }

    return cacheResultBuilder.build();
  }

  private static boolean shouldUseOnDemandData(
      CacheResultBuilder cacheResultBuilder, String serverGroupKey) {
    CacheData cacheData = cacheResultBuilder.getOnDemand().getToKeep().get(serverGroupKey);
    return cacheData != null
        && (long) cacheData.getAttributes().get("cacheTime") > cacheResultBuilder.getStartTime();
  }

  private List<YandexCloudServerGroup> getServerGroups(ProviderCache providerCache) {
    ListInstanceGroupsRequest request =
        ListInstanceGroupsRequest.newBuilder()
            .setFolderId(getFolder())
            .setView(InstanceGroupView.FULL)
            .build();
    List<InstanceGroup> instanceGroups =
        getCredentials().instanceGroupService().list(request).getInstanceGroupsList();
    return constructServerGroups(instanceGroups, providerCache);
  }

  private Optional<YandexCloudServerGroup> getServerGroup(
      String name, ProviderCache providerCache) {
    try {
      ListInstanceGroupsResponse response =
          getCredentials()
              .instanceGroupService()
              .list(
                  ListInstanceGroupsRequest.newBuilder()
                      .setFolderId(getFolder())
                      .setFilter("name='" + name + "'")
                      .setView(InstanceGroupView.FULL)
                      .build());
      List<InstanceGroup> instanceGroupsList = response.getInstanceGroupsList();
      if (instanceGroupsList.size() != 1) {
        return Optional.empty();
      }
      return constructServerGroups(response.getInstanceGroupsList(), providerCache).stream()
          .findAny();
    } catch (StatusRuntimeException ignored) {
      return Optional.empty();
    }
  }

  private List<YandexCloudServerGroup> constructServerGroups(
      List<InstanceGroup> instanceGroups, ProviderCache providerCache) {
    ListInstancesRequest listInstancesRequest =
        ListInstancesRequest.newBuilder().setFolderId(getFolder()).build();

    Map<String, YandexCloudInstance> instances =
        providerCache.getAll(INSTANCES.getNs()).stream()
            .map(
                data ->
                    getObjectMapper().convertValue(data.getAttributes(), YandexCloudInstance.class))
            .collect(toMap(YandexCloudInstance::getId, Function.identity()));
    //    Map<String, YandexCloudInstance> instances =
    //
    // getCredentials().instanceService().list(listInstancesRequest).getInstancesList().stream()
    //            .map(YandexCloudInstance::createFromProto)
    //            .collect(toMap(YandexCloudInstance::getId, Function.identity()));

    return instanceGroups.stream()
        .map(
            group -> {
              ListInstanceGroupInstancesRequest request =
                  ListInstanceGroupInstancesRequest.newBuilder()
                      .setInstanceGroupId(group.getId())
                      .build();

              Set<YandexCloudInstance> ownedInstances;
              try {
                ownedInstances =
                    getCredentials()
                        .instanceGroupService()
                        .listInstances(request)
                        .getInstancesList()
                        .stream()
                        .map(ManagedInstance::getInstanceId)
                        .map(instances::get)
                        .filter(Objects::nonNull)
                        .collect(toSet());
              } catch (StatusRuntimeException ex) {
                if (ex.getStatus() == io.grpc.Status.NOT_FOUND) {
                  ownedInstances = emptySet();
                } else {
                  throw ex;
                }
              }

              return createServerGroup(group, ownedInstances, providerCache);
            })
        .collect(toList());
  }

  private YandexCloudServerGroup createServerGroup(
      InstanceGroup group, Set<YandexCloudInstance> instances, ProviderCache providerCache) {
    YandexCloudServerGroup serverGroup = new YandexCloudServerGroup();
    serverGroup.setId(group.getId());
    serverGroup.setFolder(group.getFolderId());
    serverGroup.setName(group.getName());
    serverGroup.setServiceAccountId(group.getServiceAccountId());
    serverGroup.setType(YandexCloudProvider.ID);
    serverGroup.setCloudProvider(YandexCloudProvider.ID);
    serverGroup.setRegion("ru-central1");
    serverGroup.setCreatedTime(group.getCreatedAt().getSeconds() * 1000);
    Set<String> zones =
        group.getAllocationPolicy().getZonesList().stream()
            .map(AllocationPolicy.Zone::getZoneId)
            .collect(toSet());
    serverGroup.setZones(zones);
    serverGroup.setInstances(instances);
    serverGroup.setLaunchConfig(makeLaunchConfig(group));
    ManagedInstancesState instancesState = group.getManagedInstancesState();
    int downCount = countInstanceInState(instances, HealthState.Down);
    int upCount = countInstanceInState(instances, HealthState.Up);
    int outOfServiceCount = countInstanceInState(instances, HealthState.OutOfService);
    serverGroup.setInstanceCounts(
        ServerGroup.InstanceCounts.builder()
            .total(
                (int)
                    (instancesState.getRunningActualCount()
                        + instancesState.getRunningOutdatedCount()
                        + instancesState.getProcessingCount()))
            .unknown(
                (int)
                        (instancesState.getRunningActualCount()
                            + instancesState.getRunningOutdatedCount())
                    - downCount
                    - upCount
                    - outOfServiceCount)
            .down(downCount)
            .up(upCount)
            .starting((int) instancesState.getProcessingCount())
            .outOfService(outOfServiceCount)
            .build());
    int targetSize = (int) instancesState.getTargetSize();
    ServerGroup.Capacity.CapacityBuilder capacity =
        ServerGroup.Capacity.builder().desired(targetSize);
    if (group.getScalePolicy().hasAutoScale()) {
      capacity
          .max((int) group.getScalePolicy().getAutoScale().getMaxSize())
          .min(
              (int)
                  (group.getScalePolicy().getAutoScale().getMinZoneSize()
                      * group.getAllocationPolicy().getZonesCount()));
      serverGroup.setAutoScalePolicy(convertAutoScalePolicy(group.getScalePolicy().getAutoScale()));
    } else {
      capacity.min(targetSize);
      capacity.max(targetSize);
    }
    serverGroup.setCapacity(capacity.build());
    serverGroup.setImageSummary(
        getImageSummary(providerCache, group, group.getInstanceTemplate().getBootDiskSpec()));
    serverGroup.setImagesSummary(getImagesSummary(providerCache, group));

    serverGroup.setLabels(group.getLabelsMap());
    serverGroup.setDescription(group.getDescription());
    serverGroup.setInstanceTemplate(convertInstanceTemplate(group.getInstanceTemplate()));

    InstanceGroupOuterClass.DeployPolicy deployPolicy = group.getDeployPolicy();
    serverGroup.setDeployPolicy(
        new YandexCloudServerGroup.DeployPolicy(
            deployPolicy.getMaxUnavailable(),
            deployPolicy.getMaxExpansion(),
            deployPolicy.getMaxDeleting(),
            deployPolicy.getMaxCreating(),
            Duration.ofSeconds(deployPolicy.getStartupDuration().getSeconds())));
    serverGroup.setStatus(Status.valueOf(group.getStatusValue()));
    if (group.hasLoadBalancerState()) {
      serverGroup.setLoadBalancerIntegration(
          convertLoadBalancerIntegration(
              group.getLoadBalancerState(), group.getLoadBalancerSpec(), providerCache));
    }
    if (group.hasHealthChecksSpec()) {
      List<HealthCheckSpec> specs =
          group.getHealthChecksSpec().getHealthCheckSpecsList().stream()
              .map(
                  hc -> {
                    HealthCheckSpec.Type type =
                        hc.hasTcpOptions() ? HealthCheckSpec.Type.TCP : HealthCheckSpec.Type.HTTP;
                    long port =
                        type == HealthCheckSpec.Type.TCP
                            ? hc.getTcpOptions().getPort()
                            : hc.getHttpOptions().getPort();
                    String path =
                        type == HealthCheckSpec.Type.TCP ? "" : hc.getHttpOptions().getPath();
                    return new HealthCheckSpec(
                        type,
                        port,
                        path,
                        Duration.ofSeconds(hc.getInterval().getSeconds()),
                        Duration.ofSeconds(hc.getTimeout().getSeconds()),
                        hc.getUnhealthyThreshold(),
                        hc.getHealthyThreshold());
                  })
              .collect(toList());
      serverGroup.setHealthCheckSpecs(specs);
    }
    return serverGroup;
  }

  private static YandexCloudServerGroup.InstanceTemplate convertInstanceTemplate(
      InstanceGroupOuterClass.InstanceTemplate template) {
    YandexCloudServerGroup.InstanceTemplate instanceTemplate =
        new YandexCloudServerGroup.InstanceTemplate();
    instanceTemplate.setDescription(template.getDescription());
    instanceTemplate.setLabels(template.getLabelsMap());
    instanceTemplate.setPlatformId(template.getPlatformId());
    instanceTemplate.setResourcesSpec(
        new YandexCloudServerGroup.ResourcesSpec(
            template.getResourcesSpec().getMemory() / GB,
            template.getResourcesSpec().getCores(),
            template.getResourcesSpec().getCoreFraction() == 0
                ? 100
                : template.getResourcesSpec().getCoreFraction(),
            template.getResourcesSpec().getGpus()));
    instanceTemplate.setMetadata(template.getMetadataMap());
    instanceTemplate.setBootDiskSpec(convertAttachedDiskSpec(template.getBootDiskSpec()));
    instanceTemplate.setSecondaryDiskSpecs(
        template.getSecondaryDiskSpecsList().stream()
            .map(YandexServerGroupCachingAgent::convertAttachedDiskSpec)
            .collect(toList()));
    instanceTemplate.setNetworkInterfaceSpecs(
        template.getNetworkInterfaceSpecsList().stream()
            .map(YandexServerGroupCachingAgent::convertNetworkInterfaceSpec)
            .collect(toList()));
    YandexCloudServerGroup.InstanceTemplate.SchedulingPolicy schedulingPolicy =
        new YandexCloudServerGroup.InstanceTemplate.SchedulingPolicy();
    if (template.hasSchedulingPolicy()) {
      schedulingPolicy.setPreemptible(template.getSchedulingPolicy().getPreemptible());
    }
    instanceTemplate.setSchedulingPolicy(schedulingPolicy);
    instanceTemplate.setServiceAccountId(template.getServiceAccountId());
    return instanceTemplate;
  }

  private static YandexCloudServerGroup.NetworkInterfaceSpec convertNetworkInterfaceSpec(
      InstanceGroupOuterClass.NetworkInterfaceSpec spec) {
    return new YandexCloudServerGroup.NetworkInterfaceSpec(
        spec.getNetworkId(),
        spec.getSubnetIdsList(),
        !spec.hasPrimaryV4AddressSpec()
            ? null
            : new YandexCloudServerGroup.PrimaryAddressSpec(
                spec.getPrimaryV4AddressSpec().hasOneToOneNatSpec()),
        !spec.hasPrimaryV6AddressSpec()
            ? null
            : new YandexCloudServerGroup.PrimaryAddressSpec(
                spec.getPrimaryV6AddressSpec().hasOneToOneNatSpec()));
  }

  private static YandexCloudServerGroup.AttachedDiskSpec convertAttachedDiskSpec(
      InstanceGroupOuterClass.AttachedDiskSpec spec) {
    return new YandexCloudServerGroup.AttachedDiskSpec(
        YandexCloudServerGroup.AttachedDiskSpec.Mode.valueOf(spec.getModeValue()),
        spec.getDeviceName(),
        new YandexCloudServerGroup.AttachedDiskSpec.DiskSpec(
            spec.getDiskSpec().getDescription(),
            spec.getDiskSpec().getTypeId(),
            spec.getDiskSpec().getSize() / GB,
            spec.getDiskSpec().getImageId(),
            spec.getDiskSpec().getSnapshotId()));
  }

  private LoadBalancerIntegration convertLoadBalancerIntegration(
      LoadBalancerState state, LoadBalancerSpec spec, ProviderCache providerCache) {
    return new LoadBalancerIntegration(
        state.getTargetGroupId(),
        state.getStatusMessage(),
        new YandexCloudServerGroup.TargetGroupSpec(
            spec.getTargetGroupSpec().getName(),
            spec.getTargetGroupSpec().getDescription(),
            spec.getTargetGroupSpec().getLabelsMap()),
        convertLoadBalancer(state.getTargetGroupId(), providerCache));
  }

  private Set<YandexCloudLoadBalancer> convertLoadBalancer(
      String targetGroupId, ProviderCache providerCache) {
    if (targetGroupId == null) {
      return emptySet();
    }

    String pattern = Keys.getLoadBalancerKey("*", "*", "*", "*");
    String balancersNs = LOAD_BALANCERS.getNs();
    Collection<String> identifiers = providerCache.filterIdentifiers(balancersNs, pattern);

    return providerCache.getAll(balancersNs, identifiers).stream()
        .map(
            cacheData ->
                getObjectMapper()
                    .convertValue(cacheData.getAttributes(), YandexCloudLoadBalancer.class))
        .filter(loadBalancer -> loadBalancer.getHealths().containsKey(targetGroupId))
        .collect(Collectors.toSet());
  }

  private AutoScalePolicy convertAutoScalePolicy(ScalePolicy.AutoScale scalePolicy) {
    AutoScalePolicy policy = new AutoScalePolicy();
    policy.setMinZoneSize(scalePolicy.getMinZoneSize());
    policy.setMaxSize(scalePolicy.getMaxSize());
    policy.setMeasurementDuration(
        Duration.ofSeconds(scalePolicy.getMeasurementDuration().getSeconds()));
    policy.setWarmupDuration(Duration.ofSeconds(scalePolicy.getWarmupDuration().getSeconds()));
    policy.setStabilizationDuration(
        Duration.ofSeconds(scalePolicy.getStabilizationDuration().getSeconds()));
    policy.setInitialSize(scalePolicy.getInitialSize());
    if (scalePolicy.hasCpuUtilizationRule()) {
      double utilizationTarget = scalePolicy.getCpuUtilizationRule().getUtilizationTarget();
      policy.setCpuUtilizationRule(new CpuUtilizationRule(utilizationTarget));
    }
    List<CustomRule> customRules =
        scalePolicy.getCustomRulesList().stream()
            .map(
                rule ->
                    new CustomRule(
                        CustomRule.RuleType.valueOf(rule.getRuleTypeValue()),
                        CustomRule.MetricType.valueOf(rule.getMetricTypeValue()),
                        rule.getMetricName(),
                        rule.getTarget()))
            .collect(toList());

    policy.setCustomRules(customRules);
    return policy;
  }

  private int countInstanceInState(Set<YandexCloudInstance> instances, HealthState healthState) {
    return (int) instances.stream().filter(i -> i.getHealthState() == healthState).count();
  }

  private ServerGroup.ImagesSummary getImagesSummary(
      ProviderCache providerCache, InstanceGroup group) {
    return () ->
        Stream.concat(
                Stream.of(group.getInstanceTemplate().getBootDiskSpec()),
                group.getInstanceTemplate().getSecondaryDiskSpecsList().stream())
            .map(diskSpec -> getImageSummary(providerCache, group, diskSpec))
            .collect(toList());
  }

  private ServerGroup.ImageSummary getImageSummary(
      ProviderCache providerCache,
      InstanceGroup group,
      InstanceGroupOuterClass.AttachedDiskSpec diskSpec) {
    String imageId =
        !Strings.isNullOrEmpty(diskSpec.getDiskSpec().getImageId())
            ? diskSpec.getDiskSpec().getImageId()
            : diskSpec.getDiskSpec().getSnapshotId();

    CacheData cacheData =
        providerCache.get(IMAGES.getNs(), Keys.getImageKey("*", imageId, "*", "*"));
    if (cacheData == null) {
      return new ImageSummary(
          group.getName(), imageId, "not-found-" + imageId, emptyMap(), emptyMap());
    }

    YandexCloudImage image =
        getObjectMapper().convertValue(cacheData.getAttributes(), YandexCloudImage.class);
    return new ImageSummary(
        group.getName(),
        imageId,
        image.getName(),
        cacheData.getAttributes(),
        createBuildInfo(image.getDescription()));
  }

  @Value
  private static class ImageSummary implements ServerGroup.ImageSummary {
    String serverGroupName;
    String imageId;
    String imageName;
    Map<String, Object> image;
    Map<String, Object> buildInfo;
  }

  private HashMap<String, Object> makeLaunchConfig(InstanceGroup group) {
    HashMap<String, Object> launchConfig = new HashMap<>();
    launchConfig.put("createdTime", group.getCreatedAt().getSeconds() * 1000);

    String imageId = group.getInstanceTemplate().getBootDiskSpec().getDiskSpec().getImageId();
    String snapshotId = group.getInstanceTemplate().getBootDiskSpec().getDiskSpec().getSnapshotId();
    launchConfig.put("imageId", imageId != null ? imageId : snapshotId);

    launchConfig.put("launchConfigurationName", group.getName());
    return launchConfig;
  }

  private static Map<String, Object> createBuildInfo(@Nullable String imageDescription) {
    if (imageDescription == null) {
      return emptyMap();
    }
    Map<String, String> tags;
    try {
      tags = IMAGE_DESCRIPTION_SPLITTER.split(imageDescription);
    } catch (IllegalArgumentException e) {
      return emptyMap();
    }
    if (!tags.containsKey("appversion")) {
      return emptyMap();
    }
    AppVersion appversion = AppVersion.parseName(tags.get("appversion"));
    if (appversion == null) {
      return emptyMap();
    }
    Map<String, Object> buildInfo = new HashMap<>();
    buildInfo.put("package_name", appversion.getPackageName());
    buildInfo.put("version", appversion.getVersion());
    buildInfo.put("commit", appversion.getCommit());
    if (appversion.getBuildJobName() != null) {
      Map<String, String> jenkinsInfo = new HashMap<>();
      jenkinsInfo.put("name", appversion.getBuildJobName());
      jenkinsInfo.put("number", appversion.getBuildNumber());
      if (tags.containsKey("build_host")) {
        jenkinsInfo.put("host", tags.get("build_host"));
      }
      buildInfo.put("jenkins", jenkinsInfo);
    }
    if (tags.containsKey("build_info_url")) {
      buildInfo.put("buildInfoUrl", tags.get("build_info_url"));
    }
    return buildInfo;
  }
}
