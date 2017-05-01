package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.DCOSClient

import java.util.concurrent.ConcurrentHashMap

class DcosClientProvider {

  private final Map<String, DCOS> dcosClients = new ConcurrentHashMap<>()
  private final AccountCredentialsProvider credentialsProvider

  DcosClientProvider(AccountCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider
  }

  DCOS getDcosClient(DcosCredentials credentials) {
    String key = credentials.name
    return dcosClients.computeIfAbsent(key, { k -> DCOSClient.getInstance(credentials.dcosUrl, credentials.dcosClientConfig) })
  }

  DCOS getDcosClient(String accountName) {
    return dcosClients.computeIfAbsent(accountName, { k ->
      def credentials = credentialsProvider.getCredentials(k)
      if (!(credentials instanceof DcosCredentials)) {
        throw new IllegalArgumentException("Account [${accountName}] is not a valid DC/OS account!")
      }
      DCOSClient.getInstance(credentials.dcosUrl, credentials.dcosClientConfig)
    })
  }
}
