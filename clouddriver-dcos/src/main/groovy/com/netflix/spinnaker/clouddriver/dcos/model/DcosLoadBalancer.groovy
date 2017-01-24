package com.netflix.spinnaker.clouddriver.dcos.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import mesosphere.marathon.client.model.v2.App

import java.time.Instant

import static com.netflix.spinnaker.clouddriver.dcos.deploy.description.loadbalancer.UpsertDcosLoadBalancerAtomicOperationDescription.PortRange

//@EqualsAndHashCode(includes = ["name", "group", "account"])
class DcosLoadBalancer implements LoadBalancer, Serializable, LoadBalancerProvider.Item {
  String name
  final String type = DcosCloudProvider.ID
  final String cloudProvider = DcosCloudProvider.ID

  String region
  //String namespace
  String account
  Long createdTime
  App app
  String json
  // Set of server groups represented as maps of strings -> objects.
  Set<LoadBalancerServerGroup> serverGroups = [] as Set
  //List<String> securityGroups = []
  UpsertDcosLoadBalancerAtomicOperationDescription description

  DcosLoadBalancer(String name, String region, String accountName) {
    this.name = name
    this.region = region
    this.account = accountName
  }

  DcosLoadBalancer(App app, String region, List<DcosServerGroup> serverGroupList) {
    this.app = app
    this.json = app.toString()

    def id = DcosSpinnakerId.parse(app.id)
    this.account = id.account
    this.name = id.name
    this.region = region
    this.description = toDescription(id, app)

    // TODO is this really representative of the created time?
    this.createdTime = Instant.parse(app.versionInfo.lastConfigChangeAt).toEpochMilli()

    this.serverGroups = serverGroupList?.collect { serverGroup ->
      new LoadBalancerServerGroup(
              // TODO account not part of this model, but it appears the deck UI uses it when diffing servergroups for a loadbalancer. Causes a bug that affects at least kubernetes as well.
              name: serverGroup?.name,
              region: serverGroup?.region,
              isDisabled: serverGroup?.isDisabled(),
              instances: serverGroup?.instances?.findResults { instance ->
                // TODO if we can do this
                //if (instance.isAttached(this.name)) {

                return new LoadBalancerInstance(
                        id: instance.name,
                        zone: instance.zone,
                        health: [
                                state: instance.healthState.toString()
                        ]
                )
                //} else {
                //  return (LoadBalancerInstance) null // Groovy generics need to be convinced all control flow paths return the same object type
                //}
              } as Set,
              // TODO if we can do this
              detachedInstances: [])
    } as Set
  }

  static UpsertDcosLoadBalancerAtomicOperationDescription toDescription(DcosSpinnakerId id, App app) {

    def description = new UpsertDcosLoadBalancerAtomicOperationDescription();

    description.account = id.account
    description.name = id.name

    def names = Names.parseName(description.name)

    description.app = names.app
    description.stack = names.stack
    description.detail = names.detail

    def sortedPorts = app.portDefinitions.collect({ it.port }).sort()
    description.bindHttpHttps = sortedPorts.containsAll([80, 443])

    description.cpus = app.cpus
    description.mem = app.mem
    description.instances = app.instances

    description.acceptedResourceRoles = app.acceptedResourceRoles

    // TODO Hacking this up. Port range really won't work out in the general sense,
    // this will have to change if we want to allow arbitrary non-sequential port reservations.
    sortedPorts = sortedPorts - [80, 443, 9090, 9091]
    description.portRange = new PortRange(minPort: sortedPorts.first(), maxPort: sortedPorts.last(), protocol: app.portDefinitions.first().protocol)

    description
  }

  @Override
  @JsonIgnore
  List<LoadBalancerProvider.ByAccount> getByAccounts() {
    [new ByAccount(name: account)]
  }

  static class ByAccount implements LoadBalancerProvider.ByAccount {
    String name
    List<LoadBalancerProvider.ByRegion> byRegions = []
  }
}
