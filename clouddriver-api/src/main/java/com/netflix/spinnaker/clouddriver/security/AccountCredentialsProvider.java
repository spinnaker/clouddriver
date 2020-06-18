/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.Set;

/**
 * Implementations of this interface will provide a mechanism to store and retrieve {@link
 * AccountCredentials} objects. For manipulating the backing of this provider, consumers of this API
 * should get access to its corresponding {@link AccountCredentialsRepository}
 */
@Beta
public interface AccountCredentialsProvider {

  /**
   * Returns all of the accounts known to the repository of this provider.
   *
   * @return a set of account names
   */
  Set<? extends AccountCredentials> getAll();

  /**
   * Returns a specific {@link AccountCredentials} object a specified name
   *
   * @param name the name of the account
   * @return account credentials object
   */
  AccountCredentials getCredentials(String name);
}
