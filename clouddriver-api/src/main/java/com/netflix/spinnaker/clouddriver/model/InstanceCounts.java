/*
 * Copyright 2020 Armory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.kork.annotations.Beta;

@Beta
public interface InstanceCounts {
  /** Total number of instances in the server group */
  Integer getTotal();

  /** Total number of "Up" instances (all health indicators report "Up" or "Unknown") */
  Integer getUp();

  /** Total number of "Down" instances (at least one health indicator reports "Down") */
  Integer getDown();

  /**
   * Total number of "Unknown" instances (all health indicators report "Unknown", or no health
   * indicators reported)
   */
  Integer getUnknown();

  /**
   * Total number of "OutOfService" instances (at least one health indicator reports "OutOfService",
   * none are "Down"
   */
  Integer getOutOfService();

  /**
   * Total number of "Starting" instances (where any health indicator reports "Starting" and none
   * are "Down" or "OutOfService")
   */
  Integer getStarting();
}
