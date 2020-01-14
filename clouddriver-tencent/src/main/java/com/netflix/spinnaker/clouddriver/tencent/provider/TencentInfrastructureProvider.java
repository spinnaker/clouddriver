package com.netflix.spinnaker.clouddriver.tencent.provider;

import static com.netflix.spinnaker.clouddriver.tencent.cache.Keys.Namespace.*;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider;
import com.netflix.spinnaker.clouddriver.tencent.cache.Keys;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class TencentInfrastructureProvider extends AgentSchedulerAware
    implements SearchableProvider {
  public TencentInfrastructureProvider(Collection<? extends Agent> agents) {
    this.agents = agents;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return Keys.parse(key);
  }

  public final String getProviderName() {
    return providerName;
  }

  public final Collection<Agent> getAgents() {
    return (Collection<Agent>) agents;
  }

  public final Set<String> getDefaultCaches() {
    return defaultCaches;
  }

  public final Map<String, String> getUrlMappingTemplates() {
    return urlMappingTemplates;
  }

  public final Map<SearchableResource, SearchResultHydrator> getSearchResultHydrators() {
    return searchResultHydrators;
  }

  private final String providerName = TencentInfrastructureProvider.class.getName();
  private final Collection<? extends Agent> agents;
  private final Set<String> defaultCaches =
      ImmutableSet.copyOf(
          new HashSet<String>(
              Arrays.asList(
                  APPLICATIONS.ns,
                  CLUSTERS.ns,
                  INSTANCES.ns,
                  LOAD_BALANCERS.ns,
                  SECURITY_GROUPS.ns,
                  SERVER_GROUPS.ns)));

  {
    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
    map.put(SECURITY_GROUPS.ns, "/securityGroups/$account/$provider/$name?region=$region");
    urlMappingTemplates = map;
  }

  private final Map<String, String> urlMappingTemplates;
  private final Map<SearchableResource, SearchResultHydrator> searchResultHydrators =
      Collections.emptyMap();
}
