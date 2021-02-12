/*
 * Copyright 2021 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import java.util.Map;

public interface NamespaceProvider {

  /**
   * Looks up all of the namespaces for a particular account. Keyed on account name.
   *
   * @param account name
   * @return set of clusters with load balancers and server groups populated, or an empty set if
   *     none exist
   */
  @Empty
  Map<String, Object> getNamespaces(String account);

  /** Provider-specific identifier */
  String getCloudProvider();
}
