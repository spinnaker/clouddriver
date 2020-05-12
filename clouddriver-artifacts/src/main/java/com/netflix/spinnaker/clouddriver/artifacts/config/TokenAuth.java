/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.clouddriver.artifacts.CredentialReader;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;

@NonnullByDefault
public interface TokenAuth {
  Optional<String> getToken();

  Optional<String> getTokenFile();

  default Optional<String> getTokenAuthHeader() {
    return getTokenAsString().map(t -> "token " + t);
  }

  default Optional<String> getTokenAsString() {
    Optional<String> result = getTokenFile().map(CredentialReader::credentialsFromFile);
    if (result.isPresent()) {
      return result;
    }
    return getToken();
  }
}
