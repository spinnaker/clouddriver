/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer;
import java.util.*;

/**
 * A server group provides a relationship to many instances, and exists within a defined region and
 * one or more zones.
 */
@Beta
public interface ServerGroup {
  /**
   * The name of the server group
   *
   * @return name
   */
  String getName();

  /**
   * This resource's moniker
   *
   * @return
   */
  default Moniker getMoniker() {
    return new FriggaReflectiveNamer().deriveMoniker(this);
  }

  /**
   * Some arbitrary identifying type for this server group. May provide vendor-specific
   * identification or data-center awareness to callers.
   *
   * @deprecated use #getCloudProvider
   * @return type
   */
  String getType();

  /** Provider-specific identifier */
  String getCloudProvider();

  /**
   * The region in which the instances of this server group are known to exist.
   *
   * @return server group region
   */
  String getRegion();

  /**
   * Some vendor-specific indicator that the server group is disabled
   *
   * @return true if the server group is disabled; false otherwise
   */
  Boolean isDisabled();

  /**
   * Timestamp indicating when the server group was created
   *
   * @return the number of milliseconds after the beginning of time (1 January, 1970 UTC) when this
   *     server group was created
   */
  Long getCreatedTime();

  /**
   * The zones within a region that the instances within this server group occupy.
   *
   * @return zones of a region for which this server group has presence or is capable of having
   *     presence, or an empty set if none exist
   */
  @Empty
  Set<String> getZones();

  /**
   * The concrete instances that comprise this server group
   *
   * @return set of instances or an empty set if none exist
   */
  @Empty
  Set<? extends Instance> getInstances();

  /**
   * The names of the load balancers associated with this server group
   *
   * @return the set of load balancer names or an empty set if none exist
   */
  @Empty
  Set<String> getLoadBalancers();

  /**
   * The names of the security groups associated with this server group
   *
   * @return the set of security group names or an empty set if none exist
   */
  @Empty
  Set<String> getSecurityGroups();

  /**
   * A collection of attributes describing the launch configuration of this server group
   *
   * @return a map containing various attributes of the launch configuration
   */
  @Empty
  Map<String, Object> getLaunchConfig();

  /**
   * A collection of attributes describing the tags of this server group
   *
   * @return a map containing various tags
   */
  @Empty
  default Map<String, Object> getTags() {
    return null;
  }

  /**
   * A data structure with the total number of instances, and the number of instances reporting each
   * status
   *
   * @return a data structure
   */
  InstanceCounts getInstanceCounts();

  /**
   * The capacity (in terms of number of instances) required for the server group
   *
   * @return
   */
  Capacity getCapacity();

  /**
   * This represents all images deployed to the server group. For most providers, this will be a
   * singleton.
   */
  ImagesSummary getImagesSummary();

  /**
   * An ImageSummary is collection of data related to the build and VM image of the server group.
   * This is merely a view of data from other parts of this object.
   *
   * <p>Deprecated in favor of getImagesSummary, which is a more generic getImageSummary.
   */
  @Deprecated
  ImageSummary getImageSummary();

  default List<ServerGroupManagerSummary> getServerGroupManagers() {
    return new ArrayList<>();
  }

  default Map<String, String> getLabels() {
    return new HashMap<>();
  }

  default Map<String, Object> getExtraAttributes() {
    return Collections.EMPTY_MAP;
  }

  /**
   * Cloud provider-specific data related to the build and VM image of the server group. Deprecated
   * in favor of Images summary
   */
  public static interface ImageSummary extends Summary {
    String getServerGroupName();

    String getImageId();

    String getImageName();

    Map<String, Object> getImage();

    @Empty
    Map<String, Object> getBuildInfo();
  }

  /** Cloud provider-specific data related to the build and VM image of the server group. */
  public static interface ImagesSummary extends Summary {
    List<? extends ImageSummary> getSummaries();
  }
}
