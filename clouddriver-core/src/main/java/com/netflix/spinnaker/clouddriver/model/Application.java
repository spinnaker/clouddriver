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
import java.util.Map;
import java.util.Set;

/**
 * An application is a top-level construct that provides an association to {@link Cluster} objects.
 */
public interface Application {
  /**
   * The name of the application
   *
   * @return name
   */
  String getName();

  /**
   * Arbitrary metadata that may be associated with an application.
   *
   * @return map of key->value pairs, or an empty map
   */
  @Empty
  Map<String, String> getAttributes();

  /**
   * A set of cluster names that are associated with this application
   *
   * @return names
   */
  @Empty
  Map<String, Set<String>> getClusterNames();
}
