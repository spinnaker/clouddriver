/*
 * Copyright 2020 Coveo, Inc.
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

import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;

/** A representation of a raw resource. */
public interface RawResource {
  /**
   * Name of the raw resource
   *
   * @return name
   */
  String getName();

  /**
   * This resource's moniker
   *
   * @return moniker
   */
  default Moniker getMoniker() {
    return NamerRegistry.getDefaultNamer().deriveMoniker(this);
  }

  /**
   * The type of this raw resource. Can indicate some vendor-specific designation, or cloud provider
   *
   * @deprecated use #getCloudProvider
   * @return type
   */
  default String getType() {
    return getCloudProvider();
  }

  /** Provider-specific identifier */
  String getCloudProvider();

  /**
   * Account under which this raw resource exists.
   *
   * @return account name
   */
  String getAccount();
}
