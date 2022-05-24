/*
 * Copyright 2022 Apple Inc.
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

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Set;

@NonnullByDefault
public class AllowAllAccountSecurityPolicy implements AccountSecurityPolicy {
  @Override
  public boolean isAdmin(String username) {
    return true;
  }

  @Override
  public boolean isAccountManager(String username) {
    return true;
  }

  @Override
  public Set<String> getRoles(String username) {
    return Set.of();
  }

  @Override
  public boolean canAccessAccount(String username, String account) {
    return true;
  }

  @Override
  public boolean canModifyAccount(String username, String account) {
    return true;
  }
}
