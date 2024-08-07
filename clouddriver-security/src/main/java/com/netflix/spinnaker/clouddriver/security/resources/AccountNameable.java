/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security.resources;

import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.orchestration.OperationDescription;

/** Denotes an operation description operates on a specific account. */
public interface AccountNameable extends OperationDescription {
  String getAccount();

  /**
   * @return whether or not this operation description expects to be further restricted by one or
   *     more applications
   */
  default boolean requiresApplicationRestriction() {
    return true;
  }

  default boolean requiresAuthorization(
      SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps) {
    return true;
  }
}
