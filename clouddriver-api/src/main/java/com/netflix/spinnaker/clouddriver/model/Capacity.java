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
public interface Capacity {

  /**
   * Minimum number of instances required in this server group. If provider specific {@code
   * ServerGroup} does not have a notion of min then this should be same as {@code desired}
   */
  Integer getMin();

  /**
   * Max number of instances required in this server group. If provider specific {@code ServerGroup}
   * does not have a notion of max then this should be same as {@code desired}
   */
  Integer getMax();

  /** Desired number of instances required in this server group */
  Integer getDesired();

  /**
   * @return true if the capacity of this server group is fixed, i.e min, max and desired are all
   *     the same
   */
  boolean isPinned();
}
