package com.netflix.spinnaker.clouddriver.dcos.provider

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware
import com.netflix.spinnaker.clouddriver.cache.SearchableProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys

/**
 * @author Will Gorman
 */
class DcosProvider extends AgentSchedulerAware implements SearchableProvider {
  final DcosCloudProvider cloudProvider
  final Collection<Agent> agents

  DcosProvider(DcosCloudProvider cloudProvider, Collection<Agent> agents) {
    this.agents = agents
    this.cloudProvider = cloudProvider
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  Set<String> getDefaultCaches() {
    [Keys.Namespace.SERVER_GROUPS.ns].asImmutable()
  }

  @Override
  Map<String, String> getUrlMappingTemplates() {
    return Collections.emptyMap() //TODO: what is this?
  }

  @Override
  Map<SearchableProvider.SearchableResource, SearchableProvider.SearchResultHydrator> getSearchResultHydrators() {
    return Collections.emptyMap()  //TODO: good enough for kubernetes, do we need?
  }

  @Override
  Map<String, String> parseKey(final String key) {
    return Keys.parse(key)  //TODO most of these are still unimplemented
  }
}
