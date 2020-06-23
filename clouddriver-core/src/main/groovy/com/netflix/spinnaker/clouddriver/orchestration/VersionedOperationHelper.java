/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.orchestration;

import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@NonnullByDefault
public class VersionedOperationHelper {

  /** @deprecated ProviderVersion is going away. */
  @Deprecated
  static <T extends VersionedCloudProviderOperation> List<T> findVersionMatches(
      ProviderVersion version, List<T> converters) {
    return converters.stream().filter(o -> o.acceptsVersion(version)).collect(Collectors.toList());
  }

  static <T extends VersionedCloudProviderOperation> List<T> findVersionMatches(
      @Nullable String version, List<T> converters) {
    return converters.stream()
        .filter(it -> it.acceptsVersion(version))
        .collect(Collectors.toList());
  }
}
