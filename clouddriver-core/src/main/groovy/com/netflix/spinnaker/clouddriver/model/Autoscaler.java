/*
 * Copyright 2020 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import java.util.Set;

/**
 * A representation of an autoscaler, which may target {@link ServerGroup} objects for scaling. This
 * interface provides a contract for retrieving the name of the autoscaler and summaries of its
 * targeted server groups.
 */
public interface Autoscaler {
  /**
   * The name of the autoscaler.
   *
   * @return name
   */
  String getName();

  /**
   * Account under which this autoscaler exists.
   *
   * @return account name
   */
  String getAccount();

  /** Provider-specific identifier */
  String getCloudProvider();

  /**
   * This resource's moniker.
   *
   * @return moniker
   */
  Moniker getMoniker();

  /**
   * Server group summaries for the server groups being targeted by this autoscaler.
   *
   * @return a set of server group summaries or an empty set if none exist
   */
  @Empty
  Set<ServerGroupSummary> getServerGroupSummaries();

  /**
   * Resource labels.
   *
   * @return a map of labels
   */
  Map<String, String> getLabels();
}
