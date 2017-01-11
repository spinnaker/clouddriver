package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.loadbalancer

import com.google.common.collect.Lists
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.monitor.DcosDeploymentMonitor
import com.netflix.spinnaker.clouddriver.dcos.exception.DcosOperationException
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.Deployment
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties.LoadBalancerConfig
import static com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription.PortRange
import static DcosDeploymentMonitor.DcosDeploymentResult

class UpsertDcosLoadBalancerAtomicOperationSpec extends Specification {
  private static final ACCOUNT_NAME = "testAccount"
  private static final LOAD_BALANCER_NAME = "external"
  private static final DEPLOYMENT_ID = "deployment-id"


  def credentials
  def dcosClientProviderMock
  def dcosClientMock
  def dcosDeploymentMonitorMock
  def dcosConfigurationProperties
  def resultAppMock
  def deployment


  def existingAppMock
  def taskMock

  def setup() {
    taskMock = Mock(Task)
    TaskRepository.threadLocalTask.set(taskMock)

    existingAppMock = Mock(App)
    credentials = new DcosCredentials(ACCOUNT_NAME, "test", "test", "url", "user", "pw")
    dcosDeploymentMonitorMock = Mock(DcosDeploymentMonitor)

    def loadBalancerConfig = Mock(LoadBalancerConfig)
    dcosConfigurationProperties = Mock(DcosConfigurationProperties)
    dcosConfigurationProperties.loadBalancer >> loadBalancerConfig

    dcosClientMock = Mock(DCOS)
    dcosClientProviderMock = Stub(DcosClientProvider) {
      getDcosClient(credentials) >> dcosClientMock
    }

    resultAppMock = Mock(App)
    deployment = Mock(Deployment)

    deployment.id >> DEPLOYMENT_ID
    resultAppMock.deployments >> Lists.newArrayList(deployment)
  }

  void "UpsertDcosLoadBalancerAtomicOperation should create a new marathon-lb instance if one does not exist for the given name"() {
    setup:

    def expectedCpus = 0.1d
    def expectedInstances = 1
    def expectedMem = 256d
    def expectedMinPort = 20000
    def expectedMaxPort = 20001
    def expectedProtocol = "tcp"
    def expectedResourceRoles = ["slave_public"]
    def expectedLbImage = "marathon-lb:1.4.3"

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription(
            credentials: credentials,
            name: LOAD_BALANCER_NAME,
            cpus: expectedCpus,
            instances: expectedInstances,
            mem: expectedMem,
            acceptedResourceRoles: expectedResourceRoles,
            portRange: new PortRange(protocol: expectedProtocol, minPort: expectedMinPort, maxPort: expectedMaxPort),
            bindHttpHttps: true
    )

    def expectedAppId = DcosSpinnakerId.from(ACCOUNT_NAME, "load-balancer-$LOAD_BALANCER_NAME")
    resultAppMock.id >> expectedAppId.toString()
    dcosConfigurationProperties.loadBalancer.image >> expectedLbImage

    def successfulDeploymentResult = Mock(DcosDeploymentResult)
    successfulDeploymentResult.success >> true
    successfulDeploymentResult.deployedApp >> Optional.of(resultAppMock)

    @Subject def operation = new UpsertDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
            dcosDeploymentMonitorMock, dcosConfigurationProperties, description)

    when:
    def result = operation.operate([])

    then:
    1 * dcosClientMock.maybeApp(expectedAppId.toString()) >> Optional.empty()
    1 * dcosClientMock.createApp({ App app ->
      assert app.id == expectedAppId.toString()

      assert app.args == ["sse",
                          "-m",
                          "http://master.mesos:8080",
                          "--health-check",
                          "--haproxy-map",
                          "--group",
                          description.name]

      assert app.instances == expectedInstances
      assert app.cpus == expectedCpus
      assert app.mem == expectedMem

      assert app.env == ["HAPROXY_SSL_CERT"     : "",
                         "HAPROXY_SYSCTL_PARAMS": "net.ipv4.tcp_tw_reuse=1 net.ipv4.tcp_fin_timeout=30 net.ipv4.tcp_max_syn_backlog=10240 net.ipv4.tcp_max_tw_buckets=400000 net.ipv4.tcp_max_orphans=60000 net.core.somaxconn=10000"]

      assert app.labels == ["SPINNAKER_LOAD_BALANCER": LOAD_BALANCER_NAME]

      assert app.acceptedResourceRoles == expectedResourceRoles

      assert app.container.type == "DOCKER"
      assert app.container.docker.image == expectedLbImage
      assert !app.container.docker.isForcePullImage()
      assert app.container.docker.isPrivileged()
      assert app.container.docker.network == "HOST"

      assert app.healthChecks[0].protocol == "HTTP"
      assert app.healthChecks[0].path == "/_haproxy_health_check"
      assert app.healthChecks[0].gracePeriodSeconds == 60
      assert app.healthChecks[0].intervalSeconds == 2
      assert app.healthChecks[0].timeoutSeconds == 2
      assert app.healthChecks[0].maxConsecutiveFailures == 2
      assert app.healthChecks[0].portIndex == 0
      assert !app.healthChecks[0].ignoreHttp1xx

      assert app.portDefinitions[0].port == 9090
      assert app.portDefinitions[0].protocol == "tcp"
      assert app.portDefinitions[1].port == 9091
      assert app.portDefinitions[1].protocol == "tcp"
      assert app.portDefinitions[2].port == 80
      assert app.portDefinitions[2].protocol == "tcp"
      assert app.portDefinitions[3].port == 443
      assert app.portDefinitions[3].protocol == "tcp"
      assert app.portDefinitions[4].port == 20000
      assert app.portDefinitions[4].protocol == "tcp"
      assert app.portDefinitions[5].port == 20001
      assert app.portDefinitions[5].protocol == "tcp"

      assert app.requirePorts

      true
    }) >> resultAppMock
    1 * dcosDeploymentMonitorMock.waitForAppDeployment(dcosClientMock, resultAppMock, DEPLOYMENT_ID, null, taskMock, "UPSERT_LOAD_BALANCER") >> successfulDeploymentResult
    result == [loadBalancer: [name: expectedAppId.toString()]]
  }

  void "UpsertDcosLoadBalancerAtomicOperation should create a new marathon-lb instance excluding port definitions 80 and 443 if bindHttpHttps is false"() {
    setup:

    def expectedCpus = 0.1d
    def expectedInstances = 1
    def expectedMem = 256d
    def expectedMinPort = 20000
    def expectedMaxPort = 20001
    def expectedProtocol = "tcp"
    def expectedResourceRoles = ["slave_public"]

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription(
            credentials: credentials,
            name: LOAD_BALANCER_NAME,
            cpus: expectedCpus,
            instances: expectedInstances,
            mem: expectedMem,
            acceptedResourceRoles: expectedResourceRoles,
            portRange: new PortRange(protocol: expectedProtocol, minPort: expectedMinPort, maxPort: expectedMaxPort),
            bindHttpHttps: false
    )

    def expectedAppId = DcosSpinnakerId.from(ACCOUNT_NAME, "load-balancer-$LOAD_BALANCER_NAME")
    resultAppMock.id >> expectedAppId.toString()

    def successfulDeploymentResult = Mock(DcosDeploymentResult)
    successfulDeploymentResult.success >> true
    successfulDeploymentResult.deployedApp >> Optional.of(resultAppMock)

    @Subject def operation = new UpsertDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
            dcosDeploymentMonitorMock, dcosConfigurationProperties, description)

    when:
    def result = operation.operate([])

    then:
    1 * dcosClientMock.maybeApp(expectedAppId.toString()) >> Optional.empty()
    1 * dcosClientMock.createApp({ App app ->

      assert app.portDefinitions.size() == 4

      assert app.portDefinitions[0].port == 9090
      assert app.portDefinitions[0].protocol == "tcp"
      assert app.portDefinitions[1].port == 9091
      assert app.portDefinitions[1].protocol == "tcp"
      assert app.portDefinitions[2].port == 20000
      assert app.portDefinitions[2].protocol == "tcp"
      assert app.portDefinitions[3].port == 20001
      assert app.portDefinitions[3].protocol == "tcp"

      true
    }) >> resultAppMock
    1 * dcosDeploymentMonitorMock.waitForAppDeployment(dcosClientMock, resultAppMock, DEPLOYMENT_ID, null, taskMock, "UPSERT_LOAD_BALANCER") >> successfulDeploymentResult
    result == [loadBalancer: [name: expectedAppId.toString()]]
  }

  void "UpsertDcosLoadBalancerAtomicOperation should create a new marathon-lb instance with a service account secret and environment entry if specified in the configuration"() {
    setup:

    def expectedCpus = 0.1d
    def expectedInstances = 1
    def expectedMem = 256d
    def expectedMinPort = 20000
    def expectedMaxPort = 20001
    def expectedProtocol = "tcp"
    def expectedResourceRoles = ["slave_public"]

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription(
            credentials: credentials,
            name: LOAD_BALANCER_NAME,
            cpus: expectedCpus,
            instances: expectedInstances,
            mem: expectedMem,
            acceptedResourceRoles: expectedResourceRoles,
            portRange: new PortRange(protocol: expectedProtocol, minPort: expectedMinPort, maxPort: expectedMaxPort),
            bindHttpHttps: false
    )

    def expectedAppId = DcosSpinnakerId.from(ACCOUNT_NAME, "load-balancer-$LOAD_BALANCER_NAME")
    resultAppMock.id >> expectedAppId.toString()

    def successfulDeploymentResult = Mock(DcosDeploymentResult)
    successfulDeploymentResult.success >> true
    successfulDeploymentResult.deployedApp >> Optional.of(resultAppMock)

    dcosConfigurationProperties.loadBalancer.serviceAccountSecret >> "marathon_lb"

    @Subject def operation = new UpsertDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
            dcosDeploymentMonitorMock, dcosConfigurationProperties, description)

    when:
    def result = operation.operate([])

    then:
    1 * dcosClientMock.maybeApp(expectedAppId.toString()) >> Optional.empty()
    1 * dcosClientMock.createApp({ App app ->

      assert app.env.containsKey("DCOS_SERVICE_ACCOUNT_CREDENTIAL")
      assert app.env.get("DCOS_SERVICE_ACCOUNT_CREDENTIAL") == ["secret": "serviceCredential"]

      assert app.secrets == [serviceCredential: ["source": "marathon_lb"]]

      true
    }) >> resultAppMock
    1 * dcosDeploymentMonitorMock.waitForAppDeployment(dcosClientMock, resultAppMock, DEPLOYMENT_ID, null, taskMock, "UPSERT_LOAD_BALANCER") >> successfulDeploymentResult
    result == [loadBalancer: [name: expectedAppId.toString()]]
  }

  void "UpsertDcosLoadBalancerAtomicOperation will update a marathon-lb instance if one already exists with the same app id"() {
    setup:

    def expectedCpus = 0.1d
    def expectedInstances = 1
    def expectedMem = 256d
    def expectedMinPort = 20000
    def expectedMaxPort = 20001
    def expectedProtocol = "tcp"
    def expectedResourceRoles = ["slave_public"]
    def group = "/dev"

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription(
            credentials: credentials,
            group: group,
            name: LOAD_BALANCER_NAME,
            cpus: expectedCpus,
            instances: expectedInstances,
            mem: expectedMem,
            acceptedResourceRoles: expectedResourceRoles,
            portRange: new PortRange(protocol: expectedProtocol, minPort: expectedMinPort, maxPort: expectedMaxPort),
            bindHttpHttps: false
    )


    def expectedAppId = DcosSpinnakerId.from(ACCOUNT_NAME, group, "load-balancer-$LOAD_BALANCER_NAME")
    resultAppMock.id >> expectedAppId.toString()

    def successfulDeploymentResult = Mock(DcosDeploymentResult)
    successfulDeploymentResult.success >> true
    successfulDeploymentResult.deployedApp >> Optional.of(resultAppMock)

    def modifyAppResultMock = Mock(Result)
    modifyAppResultMock.deploymentId >> DEPLOYMENT_ID

    @Subject def operation = new UpsertDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
            dcosDeploymentMonitorMock, dcosConfigurationProperties, description)

    when:
    def result = operation.operate([])

    then:
    1 * dcosClientMock.maybeApp(expectedAppId.toString()) >> Optional.of(resultAppMock)
    1 * dcosClientMock.modifyApp(expectedAppId.toString(), { App app ->
      assert app.id == expectedAppId.toString()
      true
    }) >> modifyAppResultMock
    1 * dcosDeploymentMonitorMock.waitForAppDeployment(dcosClientMock, resultAppMock, DEPLOYMENT_ID, null, taskMock, "UPSERT_LOAD_BALANCER") >> successfulDeploymentResult
    result == [loadBalancer: [name: expectedAppId.toString()]]
  }

  void "UpsertDcosLoadBalancerAtomicOperation will throw a DcosOperationException if the DCOS deployment does not complete successfully"() {
    setup:

    def expectedCpus = 0.1d
    def expectedInstances = 1
    def expectedMem = 256d
    def expectedMinPort = 20000
    def expectedMaxPort = 20001
    def expectedProtocol = "tcp"
    def expectedResourceRoles = ["slave_public"]
    def group = "/dev"

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription(
            credentials: credentials,
            group: group,
            name: LOAD_BALANCER_NAME,
            cpus: expectedCpus,
            instances: expectedInstances,
            mem: expectedMem,
            acceptedResourceRoles: expectedResourceRoles,
            portRange: new PortRange(protocol: expectedProtocol, minPort: expectedMinPort, maxPort: expectedMaxPort),
            bindHttpHttps: false
    )


    def expectedAppId = DcosSpinnakerId.from(ACCOUNT_NAME, group, "load-balancer-$LOAD_BALANCER_NAME")
    resultAppMock.id >> expectedAppId.toString()

    def failedDeploymentResult = Mock(DcosDeploymentResult)
    failedDeploymentResult.success >> false
    failedDeploymentResult.deployedApp >> Optional.empty()

    def modifyAppResultMock = Mock(Result)
    modifyAppResultMock.deploymentId >> DEPLOYMENT_ID

    @Subject def operation = new UpsertDcosLoadBalancerAtomicOperation(dcosClientProviderMock,
            dcosDeploymentMonitorMock, dcosConfigurationProperties, description)

    when:
    operation.operate([])

    then:
    1 * dcosClientMock.maybeApp(expectedAppId.toString()) >> Optional.empty()
    1 * dcosClientMock.createApp({ App app ->
      assert app.id == expectedAppId.toString()
      true
    }) >> resultAppMock
    1 * dcosDeploymentMonitorMock.waitForAppDeployment(dcosClientMock, resultAppMock, DEPLOYMENT_ID, null, taskMock, "UPSERT_LOAD_BALANCER") >> failedDeploymentResult
    thrown(DcosOperationException)
  }
}