package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spectator.api.Registry

import java.util.concurrent.ConcurrentHashMap

import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSClient

class DcosClientProvider {

  private final Map<String, DCOS> dcosClients = new ConcurrentHashMap<>()
  private final Registry registry

  DcosClientProvider(Registry registry) {
    this.registry = registry
  }

  DCOS getDcosClient(DcosCredentials account) {
    String key = account.name
    return dcosClients.computeIfAbsent(key, { k -> DCOSClient.getInstance(account.dcosUrl, account.dcosAuthCredentials) })
  }

  DCOS getDcosClient(String account) {
    return dcosClients.get(account)
  }
}
