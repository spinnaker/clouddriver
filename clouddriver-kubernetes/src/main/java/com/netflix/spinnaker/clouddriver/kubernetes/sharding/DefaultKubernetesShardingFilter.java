/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.sharding;

import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Data;

@Data
public class DefaultKubernetesShardingFilter implements KubernetesShardingFilter {
  private Map<String, Object> config = new HashMap<>();

  @Override
  public boolean applyFilter(KubernetesConfigurationProperties.ManagedAccount account) {
    if (!config.containsKey("selectorType")) {
      throw new RuntimeException("Sharding selectorType not configured!!");
    }
    String selectorType = (String) config.get("selectorType");
    switch (selectorType) {
      case "AccountName":
        return applyAccountNameFilter(account, (String) config.get("pattern"));
      default:
        throw new RuntimeException("Unknown Sharding selectorType - " + selectorType);
    }
  }

  private boolean applyAccountNameFilter(
      KubernetesConfigurationProperties.ManagedAccount account, String pattern) {
    Pattern accountNamePattern = Pattern.compile(pattern);
    return accountNamePattern.matcher(account.getName()).matches();
  }
}
