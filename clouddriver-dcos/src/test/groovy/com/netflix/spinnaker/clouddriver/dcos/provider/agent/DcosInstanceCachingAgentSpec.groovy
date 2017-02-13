package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.GetTasksResponse
import mesosphere.marathon.client.model.v2.Task
import spock.lang.Specification

class DcosInstanceCachingAgentSpec extends Specification {
  static final private String ACCOUNT = "testAccount"
  static final private String APP = "testApp"
  static final private String CLUSTER = "${APP}-cluster"
  static final private String SERVER_GROUP = "${CLUSTER}-v000"
  DcosCredentials credentials
  AccountCredentialsRepository accountCredentialsRepository

  DcosInstanceCachingAgent subject
  private DcosClientProvider clientProvider
  private DCOS dcosClient
  ProviderCache providerCache
  private ObjectMapper objectMapper

  def setup() {
    accountCredentialsRepository = Mock(AccountCredentialsRepository)
    credentials = Stub(DcosCredentials)
    dcosClient = Mock(DCOS)
    providerCache = Mock(ProviderCache)
    objectMapper = new ObjectMapper()

    clientProvider = Mock(DcosClientProvider) {
      getDcosClient(credentials) >> dcosClient
    }

    subject = new DcosInstanceCachingAgent(ACCOUNT, credentials, clientProvider, objectMapper)
  }

  void "Should only cache marathon tasks that are owned by marathon apps under the supplied account"() {
    setup:

    def validAppId = "/${ACCOUNT}/${SERVER_GROUP}"
    def invalidAppId = "/invalidAccount/${SERVER_GROUP}"

    def validTaskId1 = "${ACCOUNT}_${SERVER_GROUP}_validtask1"
    def validTaskId2 = "${ACCOUNT}_${SERVER_GROUP}_validtask2"
    def invalidTaskId1 = "invalidAccount_${SERVER_GROUP}_invalidtask1"

    def validInstance1Key = Keys.getInstanceKey(ACCOUNT, ACCOUNT, validTaskId1)
    def validInstance2Key = Keys.getInstanceKey(ACCOUNT, ACCOUNT, validTaskId2)

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
    def providerCacheMock = Mock(ProviderCache)

    when:
    final result = subject.loadData(providerCacheMock)

    then:
    result.cacheResults.instances.size() == 2

    // Don't think the cacheResult instances are necessarily ordered, so gotta do a find
    def cacheData1 = result.cacheResults.instances.find { it.id == validInstance1Key }
    cacheData1 != null
    cacheData1.attributes.name == validTaskId1
    cacheData1.attributes.instance == new DcosInstance(validTask1, ACCOUNT)

    def cacheData2 = result.cacheResults.instances.find { it.id == validInstance2Key }
    cacheData2 != null
    cacheData2.attributes.name == validTaskId2
    cacheData2.attributes.instance == new DcosInstance(validTask2, ACCOUNT)
  }
}