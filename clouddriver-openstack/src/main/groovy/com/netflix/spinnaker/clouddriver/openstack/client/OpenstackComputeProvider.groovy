/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.client

import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackResourceNotFoundException
import org.apache.commons.lang.StringUtils
import org.openstack4j.api.Builders
import org.openstack4j.model.compute.Flavor
import org.openstack4j.model.compute.FloatingIP
import org.openstack4j.model.compute.IPProtocol;
import org.openstack4j.model.compute.RebootType
import org.openstack4j.model.compute.SecGroupExtension;
import org.openstack4j.model.compute.Server
import org.openstack4j.model.network.Port;

import java.util.List;
import java.util.Map;

/**
 * Methods for interacting with the current compute api.
 */
public class OpenstackComputeProvider implements OpenstackRequestHandler {

  @Delegate
  OpenstackIdentityProvider identityProvider

  OpenstackComputeProvider(OpenstackIdentityProvider identityProvider) {
    this.identityProvider = identityProvider
  }

  /**
   * Returns a list of instances in a given region.
   * @param region
   * @return
   */
  List<? extends Server> getInstances(String region) {
    handleRequest {
      getRegionClient(region).compute().servers().list()
    }
  }

  /**
   * Returns a map of instances grouped by server group UUID.  Matches not found are added into an unknown bucket.
   * @param region
   * @return
   */
  Map<String, List<? extends Server>> getInstancesByServerGroup(String region) {
    getInstances(region)?.groupBy { server -> server?.metadata['metering.stack'] ?: 'unknown' }
  }

  /**
   * Returns all of the console output for a given server and region.
   * @param region
   * @param serverId
   * @return
   */
  String getConsoleOutput(String region, String serverId) {
    handleRequest {
      getRegionClient(region).compute().servers().getConsoleOutput(serverId, -1)
    }
  }

  /**
   * Delete an instance.
   * @param instanceId
   * @return
   */
  void deleteInstance(String region, String instanceId) {
    handleRequest {
      getRegionClient(region).compute().servers().delete(instanceId)
    }
  }

  /**
   * Reboot an instance ... Default to SOFT reboot if not passed.
   * @param instanceId
   * @return
   */
  void rebootInstance(String region, String instanceId, RebootType rebootType = RebootType.SOFT) {
    handleRequest {
      getRegionClient(region).compute().servers().reboot(instanceId, rebootType)
    }
  }

  /**
   * Get an unallocated IP from the network, or if none are found, try to create a new floating IP in the network.
   * @param region
   * @param networkName
   * @return
   */
  FloatingIP getOrCreateFloatingIp(final String region, final String networkName) {
    handleRequest {
      FloatingIP ip = getRegionClient(region).compute().floatingIps().list().find { !it.fixedIpAddress }
      if (!ip) {
        ip = client.useRegion(region).compute().floatingIps().allocateIP(networkName)
        if (!ip) {
          throw new OpenstackProviderException("Unable to allocate new IP address on network $networkName")
        }
      }
      ip
    }
  }

  /**
   * Looks up the port associated by vip and uses the deviceId to get the attached floatingIp.
   * @param region
   * @param vipId
   * @return
   */
  FloatingIP getAssociatedFloatingIp(final String region, final String vipId) {
    Port port = getPortForVip(region, vipId)
    if (!port) {
      throw new OpenstackProviderException("Unable to find port for vip ${vipId}")
    } else {
      handleRequest {
        getRegionClient(region).compute().floatingIps().list()?.find { it.instanceId == port.deviceId }
      }
    }
  }

  /**
   * List all floating ips in the region.
   * @param region
   * @return
   */
  List<? extends FloatingIP> listFloatingIps(final String region) {
    handleRequest {
      getRegionClient(region).compute().floatingIps().list()
    }
  }

  /**
   * Deletes a security group.
   *
   * @param region the region the security group is in
   * @param securityGroupId id of the security group
   */
  void deleteSecurityGroup(String region, String securityGroupId) {
    handleRequest {
      getRegionClient(region).compute().securityGroups().delete(securityGroupId)
    }
  }

  /**
   * Deletes a security group rule
   * @param region the region to delete the rule from
   * @param id id of the rule to delete
   */
  void deleteSecurityGroupRule(String region, String id) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().deleteRule(id)
    }
  }

  /**
   * Creates a security group rule.
   *
   * If the rule is for TCP or UDP, the fromPort and toPort are used. For ICMP rules, the imcpType and icmpCode are used instead.
   *
   * @param region the region to create the rule in
   * @param securityGroupId id of the security group which this rule belongs to
   * @param protocol the protocol of the rule
   * @param cidr the cidr for the rule
   * @param remoteSecurityGroupId id of security group referenced by this rule
   * @param fromPort the fromPort for the rule
   * @param toPort the toPort for the rule
   * @param icmpType the type of the ICMP control message
   * @param icmpCode the code or subtype of the ICMP control message
   * @return the created rule
   */
  SecGroupExtension.Rule createSecurityGroupRule(String region,
                                                 String securityGroupId,
                                                 IPProtocol protocol,
                                                 String cidr,
                                                 String remoteSecurityGroupId,
                                                 Integer fromPort,
                                                 Integer toPort,
                                                 Integer icmpType,
                                                 Integer icmpCode) {

    def builder = Builders.secGroupRule()
      .parentGroupId(securityGroupId)
      .protocol(protocol)

    /*
     * Openstack/Openstack4J overload the port range to indicate ICMP type and code. This isn't immediately
     * obvious and was found through testing and inferring things from the Openstack documentation.
     */
    if (protocol == IPProtocol.ICMP) {
      builder.range(icmpType, icmpCode)
    } else {
      builder.range(fromPort, toPort)
    }

    if (remoteSecurityGroupId) {
      builder.groupId(remoteSecurityGroupId)
    } else {
      builder.cidr(cidr)
    }

    handleRequest {
      client.useRegion(region).compute().securityGroups().createRule(builder.build())
    }
  }

  /**
   * Updates a security group with the new name and description
   * @param region the region the security group is in
   * @param id the id of the security group to update
   * @param name the new name for the security group
   * @param description the new description for the security group
   * @return the updated security group
   */
  SecGroupExtension updateSecurityGroup(String region, String id, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().update(id, name, description)
    }
  }

  /**
   * Creates a security group with the given name and description
   * @return the created security group
   */
  SecGroupExtension createSecurityGroup(String region, String name, String description) {
    handleRequest {
      client.useRegion(region).compute().securityGroups().create(name, description)
    }
  }

  /**
   * Returns the security group for the given id.
   * @param region the region to look up the security group in
   * @param id id of the security group.
   */
  SecGroupExtension getSecurityGroup(String region, String id) {
    SecGroupExtension securityGroup = handleRequest {
      client.useRegion(region).compute().securityGroups().get(id)
    }
    if (!securityGroup) {
      throw new OpenstackResourceNotFoundException("Unable to find security group ${id}")
    }
    securityGroup
  }

  /**
   * Returns the list of all security groups for the given region
   */
  List<SecGroupExtension> getSecurityGroups(String region) {
    handleRequest {
      getRegionClient(region).compute().securityGroups().list()
    }
  }

  /**
   * Get a compute server based on id.
   * @param instanceId
   * @return
   */
  Server getServerInstance(String region, String instanceId) {
    Server server = handleRequest {
      client.useRegion(region).compute().servers().get(instanceId)
    }
    if (!server) {
      throw new OpenstackProviderException("Could not find server with id ${instanceId}")
    }
    server
  }

  /**
   * Returns a list of flavors by region.
   * @param region
   * @return
   */
  List<? extends Flavor> listFlavors(String region) {
    handleRequest {
      this.getRegionClient(region).compute().flavors().list()
    }
  }

  /**
   * Get an IP address from a server.
   * @param server
   * @return
   */
  String getIpForInstance(String region, String instanceId) {
    Server server = getServerInstance(region, instanceId)
    /* TODO
      For now just get the first address found. Openstack does not associate an instance id
      with load balancer membership, just an ip address. An instance can have multiple IP addresses.
      perhaps we just look for the first 192.* address found. It would also help to know the network name
      from which to choose the IP list. I am not sure if we will have that. We can certainly add that into
      the api later on when we know what data deck will have access to.
    */
    String ip = server.addresses?.addresses?.collect { n -> n.value }?.find()?.find()?.addr
    if (StringUtils.isEmpty(ip)) {
      throw new OpenstackProviderException("Instance ${instanceId} has no IP address")
    }
    ip
  }

}
