package com.netflix.spinnaker.clouddriver.dcos

import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
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

  DCOS getDcosClient(DcosAccountCredentials credentials, String clusterName) {
    def compositeKey = DcosClientCompositeKey.buildFromVerbose(credentials.account, clusterName).get()
    def trueCredentials = credentials.getCredentialsByCluster(clusterName)

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k -> DCOSClient.getInstance(trueCredentials.dcosUrl, trueCredentials.dcosConfig) })
  }

  DCOS getDcosClient(DcosClusterCredentials credentials) {
    def compositeKey = DcosClientCompositeKey.buildFrom(credentials.account, credentials.cluster).get()

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k -> DCOSClient.getInstance(credentials.dcosUrl, credentials.dcosConfig) })
  }

  DCOS getDcosClient(String accountName, String clusterName) {
    def compositeKey = DcosClientCompositeKey.buildFrom(accountName, clusterName).get()

    return dcosClients.computeIfAbsent(compositeKey.toString(), { k ->
      def credentials = credentialsProvider.getCredentials(accountName)

      if (!(credentials instanceof DcosAccountCredentials)) {
        throw new IllegalArgumentException("Account [${accountName}] is not a valid DC/OS account!")
      }

      def trueCredentials = credentials.getCredentials().getCredentialsByCluster(clusterName)

      if (!trueCredentials) {
        throw new IllegalArgumentException("Cluster [${clusterName}] is not a valid DC/OS cluster for the DC/OS account [${accountName}]!")
      }

      DCOSClient.getInstance(trueCredentials.dcosUrl, trueCredentials.dcosConfig)
    })
  }
}