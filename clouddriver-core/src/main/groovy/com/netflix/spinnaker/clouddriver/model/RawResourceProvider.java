/*
 * Copyright 2020 Coveo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import java.util.Set;

/**
 * A rawResourceProvider is an interface for the application to retrieve {@link RawResource}
 * objects. The interface provides a common contract for which one or many providers can be queried
 * for their knowledge of raw resources at a given depth of specificity.
 */
public interface RawResourceProvider {
  String getCloudProvider();

  /**
   * Returns all raw resources related to an application. A raw resource is associated to an
   * application using moniker labels or the Frigga naming conventions
   *
   * @param application the name of the application
   * @return a collection of raw resources
   */
  @Empty
  Set<? extends RawResource> getApplicationRawResources(String application);
}
