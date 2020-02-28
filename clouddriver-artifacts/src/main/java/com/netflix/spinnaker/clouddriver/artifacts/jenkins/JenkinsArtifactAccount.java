/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.jenkins;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactAccount;
import com.netflix.spinnaker.clouddriver.artifacts.config.BasicAuth;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
final class JenkinsArtifactAccount implements ArtifactAccount, BasicAuth {
  private final String name;
  private final String username;
  private final String password;
  private final String address;

  public String getUsernamePasswordFile() {
    return null;
  }
}
