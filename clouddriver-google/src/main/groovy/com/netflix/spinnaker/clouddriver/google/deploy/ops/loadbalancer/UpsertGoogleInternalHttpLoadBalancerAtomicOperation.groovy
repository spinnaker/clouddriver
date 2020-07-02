/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.BACKEND_SERVICE_NAMES
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGIONAL_LOAD_BALANCER_NAMES

@Slf4j
class UpsertGoogleInternalHttpLoadBalancerAtomicOperation extends UpsertGoogleLoadBalancerAtomicOperation {
  private static final String BASE_PHASE = "UPSERT_INTERNAL_HTTP_LOAD_BALANCER"
  private static final String PATH_MATCHER_PREFIX = "pm"
  public static final String TARGET_HTTP_PROXY_NAME_PREFIX = "target-http-proxy"
  public static final String TARGET_HTTPS_PROXY_NAME_PREFIX = "target-https-proxy"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  AtomicOperationsRegistry atomicOperationsRegistry

  @Autowired
  private GoogleNetworkProvider googleNetworkProvider

  @Autowired
  private GoogleSubnetProvider googleSubnetProvider

  @Autowired
  OrchestrationProcessor orchestrationProcessor

  @Autowired
  SafeRetry safeRetry

  private final UpsertGoogleLoadBalancerDescription description

  UpsertGoogleInternalHttpLoadBalancerAtomicOperation(UpsertGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * minimal command:
   * curl -v -X POST -H "Content-Type: application/json" -d '[{ "upsertLoadBalancer": {"credentials": "my-google-account", "loadBalancerType": "INTERNAL_MANAGED", "loadBalancerName": "internal-http-create", "portRange": "80", "backendServiceDiff": [], "defaultService": {"name": "default-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "", "hostRules": [] }}]' localhost:7002/gce/ops
   *
   * full command:
   * curl -v -X POST -H "Content-Type: application/json" -d '[{ "upsertLoadBalancer": {"credentials": "my-google-account", "loadBalancerType": "INTERNAL_MANAGED", "loadBalancerName": "internal-http-create", "portRange": "80", "backendServiceDiff": [], "defaultService": {"name": "default-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "", "hostRules": [{"hostPatterns": ["host1.com", "host2.com"], "pathMatcher": {"pathRules": [{"paths": ["/path", "/path2/more"], "backendService": {"name": "backend-service", "backends": [], "healthCheck": {"name": "health-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}}], "defaultService": {"name": "pm-backend-service", "backends": [], "healthCheck": {"name": "derp-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}}}]}}]' localhost:7002/gce/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    def network = GCEUtil.queryNetwork(description.accountName, description.network, task, BASE_PHASE, googleNetworkProvider)
    def subnet = GCEUtil.querySubnet(description.accountName, description.region, description.subnet, task, BASE_PHASE, googleSubnetProvider)
    def internalHttpLoadBalancer = new GoogleInternalHttpLoadBalancer(
        name: description.loadBalancerName,
        urlMapName: description.urlMapName,
        defaultService: description.defaultService,
        hostRules: description.hostRules,
        certificate: description.certificate,
        ipAddress: description.ipAddress,
        ipProtocol: description.ipProtocol,
        network: network.selfLink,
        subnet: subnet.selfLink,
        portRange: description.portRange
    )
    def internalHttpLoadBalancerName = internalHttpLoadBalancer.name

    task.updateStatus BASE_PHASE, "Initializing upsert of Internal HTTP load balancer $internalHttpLoadBalancerName..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region

    // Step 0: Set up state to formulate a plan for creating or updating the L7 LB.

    HashSet healthCheckExistsSet = [] as HashSet
    HashSet healthCheckNeedsUpdatedSet = [] as HashSet
    HashSet serviceExistsSet = [] as HashSet
    HashSet serviceNeedsUpdatedSet = [] as HashSet
    Boolean urlMapExists
    Boolean targetProxyExists = false
    Boolean targetProxyNeedsUpdated = false
    Boolean forwardingRuleExists

    // The following are unique on object equality, not just name. This lets us check if a service/hc exists or
    // needs updated by _name_ later.
    List<GoogleBackendService> backendServicesFromDescription = Utils.getBackendServicesFromInternalHttpLoadBalancerView(internalHttpLoadBalancer.view).unique()
    List<GoogleHealthCheck> healthChecksFromDescription = backendServicesFromDescription.collect { it.healthCheck }.unique()

    String urlMapName = internalHttpLoadBalancer?.urlMapName ?: internalHttpLoadBalancerName // An L7 load balancer is identified by its UrlMap name in Google Cloud Console.

    // Get all the existing infrastructure.

    // Look up the legacy health checks so we can do the work to transition smoothly to the UHCs.
    Set<HealthCheck> existingHealthChecks = timeExecute(
        compute.regionHealthChecks().list(project, region),
        "compute.regionHealthChecks.list",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        .getItems() as Set
    Set<BackendService> existingServices = timeExecute(
        compute.regionBackendServices().list(project, region),
        "compute.regionBackendServices.list",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        .getItems() as Set
    UrlMap existingUrlMap = null
    try {
      existingUrlMap = timeExecute(
          compute.regionUrlMaps().get(project, region, urlMapName),
          "compute.regionUrlMaps.get",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    } catch (GoogleJsonResponseException e) {
      // 404 is thrown if the url map doesn't exist. Any other exception needs to be propagated.
      if (e.getStatusCode() != 404) {
        throw e
      }
    }

    // Determine if the infrastructure in the description exists already.
    // If it does, check and see if we need to update it from the description.

    // UrlMap
    urlMapExists = existingUrlMap as Boolean

    // ForwardingRule
    ForwardingRule existingRule = null
    try {
      existingRule = timeExecute(
          compute.forwardingRules().get(project, region, internalHttpLoadBalancerName),
          "compute.forwardingRules.get",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() != 404) {
        throw e
      }
    }
    forwardingRuleExists = existingRule as Boolean

    // TargetProxy
    def existingProxy = null
    if (forwardingRuleExists) {
      String targetProxyName = GCEUtil.getLocalName(existingRule.getTarget())
      switch (Utils.getTargetProxyType(existingRule.getTarget())) {
        case GoogleTargetProxyType.HTTP:
          existingProxy = timeExecute(
              compute.regionTargetHttpProxies().get(project, region, targetProxyName),
              "compute.regionTargetHttpProxies.get",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
          // Http target proxies aren't updated. If you want to add a Https listener, there are options for that in the frontend.
          break
        case GoogleTargetProxyType.HTTPS:
          existingProxy = timeExecute(
              compute.regionTargetHttpsProxies().get(project, region, targetProxyName),
              "compute.regionTargetHttpsProxies.get",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
          if (!internalHttpLoadBalancer.certificate) {
            throw new IllegalArgumentException("${internalHttpLoadBalancerName} is an Https load balancer, but the upsert description does not contain a certificate.")
          }
          targetProxyNeedsUpdated = GCEUtil.getLocalName(existingProxy?.getSslCertificates()[0]) != GCEUtil.getLocalName(GCEUtil.buildCertificateUrl(project, internalHttpLoadBalancer.certificate))
          break
        default:
          log.warn("Unexpected target proxy type for $targetProxyName.")
          break
      }
      targetProxyExists = existingProxy as Boolean
      if (existingProxy && GCEUtil.getLocalName(existingProxy.getUrlMap()) != description.urlMapName) {
        throw new IllegalStateException(
          "Listener with name ${existingRule.getName()} already exists and points to url map: ${GCEUtil.getLocalName(existingProxy.getUrlMap())}," +
            " which is different from the description url map: ${description.urlMapName}."
        )
      }
    }

    // HealthChecks
    if (healthChecksFromDescription.size() != healthChecksFromDescription.unique(false) { it.name }.size()) {
      throw new GoogleOperationException("Duplicate health checks with different attributes in the description. " +
        "Please specify one object per named health check.")
    }

    healthChecksFromDescription.each { GoogleHealthCheck healthCheck ->
      String healthCheckName = healthCheck.name

      def existingHealthCheck = existingHealthChecks.find { it.name == healthCheckName }
      if (existingHealthCheck) {
        healthCheckExistsSet.add(healthCheck.name)
        if (GCEUtil.healthCheckShouldBeUpdated(existingHealthCheck, healthCheck)) {
          healthCheckNeedsUpdatedSet.add(healthCheck.name)
        }
      }
    }

    // BackendServices
    if (backendServicesFromDescription.size() != backendServicesFromDescription.unique(false) { it.name }.size()) {
      throw new GoogleOperationException("Duplicate backend services with different attributes in the description. " +
        "Please specify one object per named backend service.")
    }

    backendServicesFromDescription.each { GoogleBackendService backendService ->
      String backendServiceName = backendService.name

      def existingService = existingServices.find { it.name == backendServiceName }
      if (existingService) {
        serviceExistsSet.add(backendService.name)

        Boolean differentHealthChecks = existingService.getHealthChecks().collect { GCEUtil.getLocalName(it) } != [backendService.healthCheck.name]
        Boolean differentSessionAffinity = GoogleSessionAffinity.valueOf(existingService.getSessionAffinity()) != backendService.sessionAffinity
        Boolean differentSessionCookieTtl = existingService.getAffinityCookieTtlSec() != backendService.affinityCookieTtlSec
        Boolean differentPortName = existingService.getPortName() != backendService.portName
        Boolean differentConnectionDraining = existingService.getConnectionDraining()?.getDrainingTimeoutSec() != backendService?.connectionDrainingTimeoutSec
        if (differentHealthChecks || differentSessionAffinity || differentSessionCookieTtl || differentPortName || differentConnectionDraining) {
          serviceNeedsUpdatedSet.add(backendService.name)
        }
      }
    }

    // Step 1: If there are no existing components in GCE, insert the new L7 components.
    // If something exists and needs updated, update it. Else do nothing.

    // HealthChecks
    healthChecksFromDescription.each { GoogleHealthCheck healthCheck ->
      String healthCheckName = healthCheck.name

      if (!healthCheckExistsSet.contains(healthCheck.name)) {
        task.updateStatus BASE_PHASE, "Creating health check $healthCheckName in $region..."
        HealthCheck newHealthCheck = GCEUtil.createNewHealthCheck(healthCheck)
        def insertHealthCheckOperation = timeExecute(
              compute.regionHealthChecks().insert(project, region, newHealthCheck),
              "compute.regionHealthChecks.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        googleOperationPoller.waitForRegionalOperation(compute, project, region, insertHealthCheckOperation.getName(),
          null, task, "region health check " + healthCheckName, BASE_PHASE)
      } else if (healthCheckExistsSet.contains(healthCheck.name) &&
                 healthCheckNeedsUpdatedSet.contains(healthCheck.name)) {
        task.updateStatus BASE_PHASE, "Updating health check $healthCheckName..."
        def hcToUpdate = existingHealthChecks.find { it.name == healthCheckName }
        GCEUtil.updateExistingHealthCheck(hcToUpdate, healthCheck)
        def updateHealthCheckOperation = timeExecute(
           compute.regionHealthChecks().update(project, region, healthCheckName, hcToUpdate),
           "compute.regionHealthChecks.update",
           TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        googleOperationPoller.waitForRegionalOperation(compute, project, region, updateHealthCheckOperation.getName(),
          null, task, "region health check $healthCheckName", BASE_PHASE)
      }
    }

    // BackendServices
    backendServicesFromDescription.each { GoogleBackendService backendService ->
      String backendServiceName = backendService.name
      String sessionAffinity = backendService?.sessionAffinity?.toString() ?: 'NONE'

      if (!serviceExistsSet.contains(backendService.name)) {
        task.updateStatus BASE_PHASE, "Creating backend service $backendServiceName in $region..."
        BackendService bs = new BackendService(
          name: backendServiceName,
          loadBalancingScheme: "INTERNAL_MANAGED",
          portName: backendService.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME,
          connectionDraining: new ConnectionDraining().setDrainingTimeoutSec(backendService.connectionDrainingTimeoutSec),
          healthChecks: [GCEUtil.buildRegionalHealthCheckUrl(project, region, backendService.healthCheck.name)],
          sessionAffinity: sessionAffinity,
          affinityCookieTtlSec: backendService.affinityCookieTtlSec
        )
        def insertBackendServiceOperation = timeExecute(
                compute.regionBackendServices().insert(project, region, bs),
                "compute.regionBackendServices.insert",
                TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        googleOperationPoller.waitForRegionalOperation(compute, project, region, insertBackendServiceOperation.getName(),
          null, task, "region backend service " + backendServiceName, BASE_PHASE)
      } else if (serviceExistsSet.contains(backendService.name)) {
        // Update the actual backend service if necessary.
        if (serviceNeedsUpdatedSet.contains(backendService.name)) {
          task.updateStatus BASE_PHASE, "Updating backend service $backendServiceName in $region..."
          def bsToUpdate = existingServices.find { it.name == backendServiceName }
          def hcName = backendService.healthCheck.name
          bsToUpdate.portName = backendService.portName ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME
          bsToUpdate.connectionDraining = new ConnectionDraining().setDrainingTimeoutSec(backendService.connectionDrainingTimeoutSec)
          bsToUpdate.healthChecks = [GCEUtil.buildRegionalHealthCheckUrl(project, region, hcName)]
          bsToUpdate.sessionAffinity = sessionAffinity
          bsToUpdate.affinityCookieTtlSec = backendService.affinityCookieTtlSec

          def updateServiceOperation = timeExecute(
                  compute.regionBackendServices().update(project, region, backendServiceName, bsToUpdate),
                  "compute.regionBackendServices.update",
                  TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
          googleOperationPoller.waitForRegionalOperation(compute, project, region, updateServiceOperation.getName(),
            null, task, "region backend service  $backendServiceName", BASE_PHASE)
        }

        fixBackendMetadata(compute, description.credentials, project, atomicOperationsRegistry, orchestrationProcessor, description.loadBalancerName, backendService)
      }
    }

    description?.backendServiceDiff?.each { GoogleBackendService backendService ->
      fixBackendMetadata(compute, description.credentials, project, atomicOperationsRegistry, orchestrationProcessor, description.loadBalancerName, backendService)
    }

    // UrlMap
    UrlMap urlMapToUpdate = null
    def urlMapUrl = null
    if (!urlMapExists) {
      task.updateStatus BASE_PHASE, "Creating URL map $urlMapName in $region..."
      UrlMap newUrlMap = new UrlMap(name: urlMapName, hostRules: [], pathMatchers: [])
      newUrlMap.defaultService = GCEUtil.buildRegionBackendServiceUrl(project, region, internalHttpLoadBalancer.defaultService.name)
      internalHttpLoadBalancer?.hostRules?.each { GoogleHostRule hostRule ->
        String pathMatcherName = "$PATH_MATCHER_PREFIX-${UUID.randomUUID().toString()}"
        def pathMatcher = hostRule.pathMatcher
        PathMatcher newPathMatcher = new PathMatcher(
          name: pathMatcherName,
          defaultService: GCEUtil.buildRegionBackendServiceUrl(project, region, pathMatcher.defaultService.name),
          pathRules: pathMatcher.pathRules.collect {
            new PathRule(
              paths: it.paths,
              service: GCEUtil.buildRegionBackendServiceUrl(project, region, it.backendService.name)
            )
          }
        )
        newUrlMap.pathMatchers << newPathMatcher
        newUrlMap.hostRules << new HostRule(pathMatcher: pathMatcherName, hosts: hostRule.hostPatterns)
      }
      def insertUrlMapOperation = timeExecute(
              compute.regionUrlMaps().insert(project, region, newUrlMap),
              "compute.regionUrlMaps.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      googleOperationPoller.waitForRegionalOperation(compute, project, region, insertUrlMapOperation.getName(),
        null, task, "region url map " + urlMapName, BASE_PHASE)
      urlMapUrl = insertUrlMapOperation.getTargetLink()
    } else if (urlMapExists) {
      task.updateStatus BASE_PHASE, "Updating URL map $urlMapName in $region..."
      existingUrlMap.defaultService = GCEUtil.buildRegionBackendServiceUrl(project, region, internalHttpLoadBalancer.defaultService.name)
      existingUrlMap.pathMatchers = []
      existingUrlMap.hostRules = []

      internalHttpLoadBalancer?.hostRules?.each { GoogleHostRule hostRule ->
        String pathMatcherName = "$PATH_MATCHER_PREFIX-${UUID.randomUUID().toString()}"
        def pathMatcher = hostRule.pathMatcher
        PathMatcher newPathMatcher = new PathMatcher(
          name: pathMatcherName,
          defaultService: GCEUtil.buildRegionBackendServiceUrl(project, region, pathMatcher.defaultService.name),
          pathRules: pathMatcher.pathRules.collect {
            new PathRule(
              paths: it.paths,
              service: GCEUtil.buildRegionBackendServiceUrl(project, region, it.backendService.name)
            )
          }
        )
        existingUrlMap.pathMatchers << newPathMatcher
        existingUrlMap.hostRules << new HostRule(pathMatcher: pathMatcherName, hosts: hostRule.hostPatterns)
      }
      def updateUrlMapOperation = timeExecute(
              compute.regionUrlMaps().update(project, region, urlMapName, existingUrlMap),
              "compute.regionUrlMaps.update",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      googleOperationPoller.waitForRegionalOperation(compute, project, region, updateUrlMapOperation.getName(),
        null, task, "region url map $urlMapName", BASE_PHASE)
      urlMapUrl = updateUrlMapOperation.getTargetLink()
    } else {
      urlMapUrl = existingUrlMap.getSelfLink()
    }

    // TargetProxy
    String targetProxyName
    def targetProxy
    def insertTargetProxyOperation
    String targetProxyUrl
    if (!targetProxyExists) {
      if (internalHttpLoadBalancer.certificate) {
        targetProxyName = "$internalHttpLoadBalancerName-$TARGET_HTTPS_PROXY_NAME_PREFIX"
        task.updateStatus BASE_PHASE, "Creating target proxy $targetProxyName in $region..."
        targetProxy = new TargetHttpsProxy(
          name: targetProxyName,
          sslCertificates: [GCEUtil.buildCertificateUrl(project, internalHttpLoadBalancer.certificate)],
          urlMap: urlMapUrl,
        )
        insertTargetProxyOperation = timeExecute(
            compute.regionTargetHttpsProxies().insert(project, region, targetProxy),
            "compute.regionTargetHttpsProxies.insert",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      } else {
        targetProxyName = "$internalHttpLoadBalancerName-$TARGET_HTTP_PROXY_NAME_PREFIX"
        task.updateStatus BASE_PHASE, "Creating target proxy $targetProxyName in $region..."
        targetProxy = new TargetHttpProxy(name: targetProxyName, urlMap: urlMapUrl)
        insertTargetProxyOperation = timeExecute(
            compute.regionTargetHttpProxies().insert(project, region, targetProxy),
            "compute.regionTargetHttpProxies.insert",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      }

      googleOperationPoller.waitForRegionalOperation(compute, project, region, insertTargetProxyOperation.getName(),
        null, task, "region target proxy $targetProxyName", BASE_PHASE)
      targetProxyUrl = insertTargetProxyOperation.getTargetLink()
    } else if (targetProxyExists && targetProxyNeedsUpdated) {
      GoogleTargetProxyType proxyType = Utils.getTargetProxyType(existingProxy?.getSelfLink())
      switch (proxyType) {
        case GoogleTargetProxyType.HTTP:
          // Http target proxies aren't updated. If you want to add a Https listener, there are options for that in the frontend.
          break
        case GoogleTargetProxyType.HTTPS:
          targetProxyName = "$internalHttpLoadBalancerName-$TARGET_HTTPS_PROXY_NAME_PREFIX"
          task.updateStatus BASE_PHASE, "Updating target proxy $targetProxyName in $region..."
          TargetHttpsProxiesSetSslCertificatesRequest setSslReq = new TargetHttpsProxiesSetSslCertificatesRequest(
            sslCertificates: [GCEUtil.buildRegionalCertificateUrl(project, region, internalHttpLoadBalancer.certificate)],
          )
          def sslCertOp = timeExecute(
              compute.regionTargetHttpsProxies().setSslCertificates(project, region, targetProxyName, setSslReq),
              "compute.regionTargetHttpsProxies.setSslCertificates",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
          googleOperationPoller.waitForRegionalOperation(compute, project, region, sslCertOp.getName(), null, task,
            "set ssl cert ${internalHttpLoadBalancer.certificate}", BASE_PHASE)
          UrlMapReference urlMapRef = new UrlMapReference(urlMap: urlMapUrl)
          def setUrlMapOp = timeExecute(
                  compute.regionTargetHttpsProxies().setUrlMap(project, region, targetProxyName, urlMapRef),
                  "compute.regionTargetHttpsProxies.setUrlMap",
                  TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
          googleOperationPoller.waitForRegionalOperation(compute, project, region, setUrlMapOp.getName(), null, task,
            "set urlMap $urlMapUrl for target proxy $targetProxyName", BASE_PHASE)
          targetProxyUrl = setUrlMapOp.getTargetLink()
          break
        default:
          throw new IllegalStateException("Updating Internal Http load balancer $internalHttpLoadBalancerName in $region failed. " +
            "Could not update target proxy $targetProxyName; Illegal target proxy type $proxyType.")
          break
      }
    } else {
      targetProxyUrl = existingProxy.getSelfLink()
    }

    // ForwardingRule
    if (!forwardingRuleExists) {
      task.updateStatus BASE_PHASE, "Creating internal forwarding rule $internalHttpLoadBalancerName in $region..."
      def forwardingRule = new ForwardingRule(
        name: internalHttpLoadBalancerName,
        loadBalancingScheme: 'INTERNAL_MANAGED',
        IPAddress: internalHttpLoadBalancer.ipAddress,
        IPProtocol: internalHttpLoadBalancer.ipProtocol,
        network: internalHttpLoadBalancer.network,
        subnetwork: internalHttpLoadBalancer.subnet,
        portRange: internalHttpLoadBalancer.certificate ? "443" : internalHttpLoadBalancer.portRange,
        target: targetProxyUrl,
      )
      Operation forwardingRuleOp = safeRetry.doRetry(
          { timeExecute(
              compute.forwardingRules().insert(project, region, forwardingRule),
              "compute.forwardingRules.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
          "forwarding rule ${description.loadBalancerName}",
          task,
          [400, 403, 412],
          [],
          [action: "insert", phase: BASE_PHASE, operation: "compute.forwardingRules.insert", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
          registry
      ) as Operation

      // Orca's orchestration for upserting a Google load balancer does not contain a task
      // to wait for the state of the platform to show that a load balancer was created (for good reason,
      // that would be a complicated operation). Instead, Orca waits for Clouddriver to execute this operation
      // and do a force cache refresh. We should wait for the whole load balancer to be created in the platform
      // before we exit this upsert operation, so we wait for the forwarding rule to be created before continuing
      // so we _know_ the state of the platform when we do a force cache refresh.
      googleOperationPoller.waitForRegionalOperation(compute, project, region, forwardingRuleOp.getName(),
          null, task, "forwarding rule " + internalHttpLoadBalancerName, BASE_PHASE)
    }
    // NOTE: there is no update for forwarding rules because we support adding/deleting multiple listeners in the frontend.
    // Rotating or changing certificates updates the targetProxy only, so the forwarding rule doesn't need to change.

    // Delete extraneous listeners.
    description.listenersToDelete?.each { String forwardingRuleName ->
      task.updateStatus BASE_PHASE, "Deleting listener ${forwardingRuleName} in $region..."
      GCEUtil.deleteRegionalListener(compute, project, region, forwardingRuleName, BASE_PHASE, safeRetry, this)
    }

    task.updateStatus BASE_PHASE, "Done upserting Internal HTTP load balancer $internalHttpLoadBalancerName in $region"
    [loadBalancers: [("region"): [name: internalHttpLoadBalancerName]]]
  }

  /**
   * Update each instance template on all the server groups in the backend service to reflect being added to the new load balancer.
   * @param compute
   * @param credentials
   * @param project
   * @param loadBalancerName
   * @param backendService
   */
  private void fixBackendMetadata(Compute compute,
                                  GoogleNamedAccountCredentials credentials,
                                  String project,
                                  AtomicOperationsRegistry atomicOperationsRegistry,
                                  OrchestrationProcessor orchestrationProcessor,
                                  String loadBalancerName,
                                  GoogleBackendService backendService) {
    backendService.backends.each { GoogleLoadBalancedBackend backend ->
      def groupName = Utils.getLocalName(backend.serverGroupUrl)
      def groupRegion = Utils.getRegionFromGroupUrl(backend.serverGroupUrl)

      String templateUrl = null
      switch (Utils.determineServerGroupType(backend.serverGroupUrl)) {
        case GoogleServerGroup.ServerGroupType.REGIONAL:
          templateUrl = timeExecute(
              compute.regionInstanceGroupManagers().get(project, groupRegion, groupName),
              "compute.regionInstanceGroupManagers.get",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, groupRegion)
              .getInstanceTemplate()
          break
        case GoogleServerGroup.ServerGroupType.ZONAL:
          def groupZone = Utils.getZoneFromGroupUrl(backend.serverGroupUrl)
          templateUrl = timeExecute(
              compute.instanceGroupManagers().get(project, groupZone, groupName),
              "compute.instanceGroupManagers.get",
              TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, groupZone)
              .getInstanceTemplate()
          break
        default:
          throw new IllegalStateException("Server group referenced by ${backend.serverGroupUrl} has illegal type.")
          break
      }

      InstanceTemplate template = timeExecute(
          compute.instanceTemplates().get(project, Utils.getLocalName(templateUrl)),
          "compute.instancesTemplates.get",
          TAG_SCOPE, SCOPE_GLOBAL)
      def instanceDescription = GCEUtil.buildInstanceDescriptionFromTemplate(project, template)

      def templateOpMap = [
        image              : instanceDescription.image,
        instanceType       : instanceDescription.instanceType,
        credentials        : credentials.getName(),
        disks              : instanceDescription.disks,
        instanceMetadata   : instanceDescription.instanceMetadata,
        tags               : instanceDescription.tags,
        network            : instanceDescription.network,
        subnet             : instanceDescription.subnet,
        serviceAccountEmail: instanceDescription.serviceAccountEmail,
        authScopes         : instanceDescription.authScopes,
        preemptible        : instanceDescription.preemptible,
        automaticRestart   : instanceDescription.automaticRestart,
        onHostMaintenance  : instanceDescription.onHostMaintenance,
        region             : groupRegion,
        serverGroupName    : groupName
      ]

      if (instanceDescription.minCpuPlatform) {
        templateOpMap.minCpuPlatform = instanceDescription.minCpuPlatform
      }

      def instanceMetadata = templateOpMap?.instanceMetadata
      if (instanceMetadata) {
        List<String> regionalLbs = instanceMetadata.(REGIONAL_LOAD_BALANCER_NAMES)?.split(',') ?: []
        regionalLbs = regionalLbs  ? regionalLbs + loadBalancerName : [loadBalancerName]
        instanceMetadata.(REGIONAL_LOAD_BALANCER_NAMES) = regionalLbs.unique().join(',')

        List<String> bsNames = instanceMetadata.(BACKEND_SERVICE_NAMES)?.split(',') ?: []
        bsNames = bsNames ? bsNames + backendService.name : [backendService.name]
        instanceMetadata.(BACKEND_SERVICE_NAMES) = bsNames.unique().join(',')
      } else {
        templateOpMap.instanceMetadata = [
          (REGIONAL_LOAD_BALANCER_NAMES): loadBalancerName,
          (BACKEND_SERVICE_NAMES)     : backendService.name,
        ]
      }

      def converter = atomicOperationsRegistry.getAtomicOperationConverter('modifyGoogleServerGroupInstanceTemplateDescription', 'gce', ProviderVersion.v1)
      AtomicOperation templateOp = converter.convertOperation(templateOpMap)
      orchestrationProcessor.process([templateOp], UUID.randomUUID().toString())
    }
  }
}
