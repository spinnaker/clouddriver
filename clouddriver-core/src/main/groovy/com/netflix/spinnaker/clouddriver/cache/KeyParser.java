/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import java.util.Map;

public interface KeyParser {

  /**
   * Indicates which provider this particular parser handles
   * @return the cloud provider ID
   */
  String getCloudProvider();

  /**
   * Parses the supplied key to an arbitrary Map of attributes
   * @param key the full key
   * @return a Map of the key attributes
   */
  Map<String, String> parseKey(String key);

  /**
   * indicates whether this parser can parse the supplied type
   * @param type the entity type, typically corresponding to a value in the implementing class's Namespace
   * @return true if it can parse this type
   */
  Boolean canParse(String type);
}
