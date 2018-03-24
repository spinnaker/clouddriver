/*
 * Copyright 2018 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.dcos.security.DcosClusterCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.GetTasksResponse
import mesosphere.marathon.client.model.v2.Task
import spock.lang.Specification

class DcosInstanceCachingAgentSpec extends Specification {
  static final private String ACCOUNT = "testaccount"
  static final private String APP = "testapp"
  static final private String REGION = "default"
  static final private String CLUSTER = "${APP}-cluster"
  static final private String SERVER_GROUP = "${CLUSTER}-v000"
  static final private String DCOS_URL = "https://test.com/"

  DcosAccountCredentials credentials
  DcosClusterCredentials clusterCredentials
  AccountCredentialsRepository accountCredentialsRepository
  DcosInstanceCachingAgent subject
  private DcosClientProvider clientProvider
  private DCOS dcosClient
  ProviderCache providerCache
  private ObjectMapper objectMapper

  def setup() {
    accountCredentialsRepository = Mock(AccountCredentialsRepository)
    credentials = Stub(DcosAccountCredentials)
    clusterCredentials = Stub(DcosClusterCredentials)
    dcosClient = Mock(DCOS)
    providerCache = Mock(ProviderCache)
    objectMapper = new ObjectMapper()

    clientProvider = Mock(DcosClientProvider) {
      getDcosClient(credentials, REGION) >> dcosClient
    }

    credentials.getCredentialsByCluster(REGION) >> clusterCredentials
    clusterCredentials.dcosUrl >> DCOS_URL

    subject = new DcosInstanceCachingAgent(ACCOUNT, REGION, credentials, clientProvider, objectMapper)
  }

  void "Should only cache marathon tasks that are owned by marathon apps under the supplied account"() {
    setup:

    def validAppId = "/${ACCOUNT}/${SERVER_GROUP}"
    def invalidAppId = "/invalidAccount/${SERVER_GROUP}"

    def validTaskId1 = "${ACCOUNT}_${SERVER_GROUP}_validtask1"
    def validTaskId2 = "${ACCOUNT}_${SERVER_GROUP}_validtask2"
    def invalidTaskId1 = "invalidAccount_${SERVER_GROUP}_invalidtask1"

    def validInstance1Key = Keys.getInstanceKey(ACCOUNT, REGION, validTaskId1)
    def validInstance2Key = Keys.getInstanceKey(ACCOUNT, REGION, validTaskId2)

    def validTask1 = Mock(Task) {
      getId() >> validTaskId1
      getAppId() >> validAppId
    }

    def validTask2 = Mock(Task) {
      getId() >> validTaskId2
      getAppId() >> validAppId
    }

    def invalidTask1 = Mock(Task) {
      getId() >> invalidTaskId1
      getAppId() >> invalidAppId
    }

    GetTasksResponse allTasks = Mock(GetTasksResponse) {
      getTasks() >> [validTask1, validTask2, invalidTask1]
    }

    dcosClient.getTasks() >> allTasks
    dcosClient.getDeployments() >> []
    def providerCacheMock = Mock(ProviderCache)

    when:
    final result = subject.loadData(providerCacheMock)

    then:
    result.cacheResults.instances.size() == 2

    // Don't think the cacheResult instances are necessarily ordered, so gotta do a find
    def cacheData1 = result.cacheResults.instances.find { it.id == validInstance1Key }
    cacheData1 != null
    cacheData1.attributes.name == validTaskId1
    cacheData1.attributes.instance == new DcosInstance(validTask1, ACCOUNT, REGION, DCOS_URL, false)

    def cacheData2 = result.cacheResults.instances.find { it.id == validInstance2Key }
    cacheData2 != null
    cacheData2.attributes.name == validTaskId2
    cacheData2.attributes.instance == new DcosInstance(validTask2, ACCOUNT, REGION, DCOS_URL, false)
  }
}
