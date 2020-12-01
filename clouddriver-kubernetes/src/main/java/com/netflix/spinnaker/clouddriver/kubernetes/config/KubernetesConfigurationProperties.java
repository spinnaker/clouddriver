/*
 * Copyright 2019 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.model.PropertiesMapExtractor;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

@Data
@Slf4j
public class KubernetesConfigurationProperties {
  private static final int DEFAULT_CACHE_THREADS = 1;
  private List<ManagedAccount> accounts = new ArrayList<>();
  private RawResourcesEndpointConfig rawResourcesEndpointConfig = new RawResourcesEndpointConfig();

  @Data
  public static class ManagedAccount implements CredentialsDefinition {
    private String name;
    private String environment;
    private String accountType;
    private String context;
    private String oAuthServiceAccount;
    private List<String> oAuthScopes;
    private String kubeconfigFile;
    private String kubeconfigContents;
    private String kubectlExecutable;
    private Integer kubectlRequestTimeoutSeconds;
    private boolean serviceAccount = false;
    private List<String> namespaces = new ArrayList<>();
    private List<String> omitNamespaces = new ArrayList<>();
    private int cacheThreads = DEFAULT_CACHE_THREADS;
    private List<String> requiredGroupMembership = new ArrayList<>();
    private Permissions.Builder permissions = new Permissions.Builder();
    private String namingStrategy = "kubernetesAnnotations";
    private boolean debug = false;
    private boolean metrics = true;
    private boolean checkPermissionsOnStartup = true;
    private List<CustomKubernetesResource> customResources = new ArrayList<>();
    private List<KubernetesCachingPolicy> cachingPolicies = new ArrayList<>();
    private List<String> kinds = new ArrayList<>();
    private List<String> omitKinds = new ArrayList<>();
    private boolean onlySpinnakerManaged = false;
    private Long cacheIntervalSeconds;
    private boolean cacheAllApplicationRelationships = false;

    public void validate() {
      if (Strings.isNullOrEmpty(name)) {
        throw new IllegalArgumentException("Account name for Kubernetes provider missing.");
      }

      if (!omitNamespaces.isEmpty() && !namespaces.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'namespaces' and 'omitNamespaces' can be specified");
      }

      if (!omitKinds.isEmpty() && !kinds.isEmpty()) {
        throw new IllegalArgumentException(
            "At most one of 'kinds' and 'omitKinds' can be specified");
      }
    }
  }

  public KubernetesConfigurationProperties() {}

  /**
   * For larger number of Kubernetes accounts, SpringBoot based properties binding is taking tens of
   * minutes, hence this custom constructor for KubernetesConfigurationProperties is needed. Spring
   * Cloud Config's BootstrapPropertySource stores the properties in a canonical form so a regex
   * pattern is used to extract the property keys.
   *
   * @param propertiesMapExtractor This provides a map of Kubernetes account properties
   */
  public KubernetesConfigurationProperties(PropertiesMapExtractor propertiesMapExtractor) {
    Map<String, Object> originalPropertiesMap = propertiesMapExtractor.getPropertiesMap();
    Map<String, Object> accountPropertiesMap = null;
    String propertyRegex =
        "kubernetes\\.accounts\\[(\\d+)\\]\\.(\\w+)(\\[(\\d+)\\])?(\\.(\\w+))?(\\[(\\d+)\\])?";
    BeanWrapper wrapper = null, crdWrapper = null;
    Permissions.Builder accountPermissions = null;
    boolean accountExist = false, permissionsExist = false, crdExist = false;
    Pattern iPattern = Pattern.compile(propertyRegex);
    Matcher match;
    String propertyName;
    int currentAccIndex = 0, tempPropertyIndex;
    int currentCrdIndex = 0, tempCrdIndex = 0;
    Object value;
    for (Map.Entry<String, Object> entry : originalPropertiesMap.entrySet()) {
      value = entry.getValue();
      match = iPattern.matcher(entry.getKey());
      if (match.matches() && value != null && value != "") {
        tempPropertyIndex = Integer.parseInt(match.group(1));
        propertyName = match.group(2);
        if (tempPropertyIndex != currentAccIndex) {
          // settle previous account
          wrapper.setPropertyValues(accountPropertiesMap);
          // setup for new Account
          currentAccIndex = tempPropertyIndex;
          accountExist = false;
          permissionsExist = false;
          currentCrdIndex = 0;
          crdExist = false;
        }
        if (!accountExist) {
          wrapper = new BeanWrapperImpl(ManagedAccount.class);
          getAccounts().add((ManagedAccount) wrapper.getWrappedInstance());
          accountExist = true;
          accountPropertiesMap = new HashMap<>();
        }
        if (propertyName.equals("permissions")) {
          if (!permissionsExist) {
            accountPermissions = new Permissions.Builder();
            accountPropertiesMap.put("permissions", accountPermissions);
            permissionsExist = true;
          }
          accountPermissions
              .computeIfAbsent(Authorization.valueOf(match.group(6)), a -> new ArrayList<>())
              .add((String) value); // match.group(6) is Authorization string
        } else if (propertyName.equals("customResources")) {
          tempCrdIndex = Integer.parseInt(match.group(4)); // match.group(4) is the index of crd
          if (currentCrdIndex != tempCrdIndex) {
            currentCrdIndex = tempCrdIndex;
            crdExist = false;
          }
          if (!crdExist) {
            crdWrapper = new BeanWrapperImpl(CustomKubernetesResource.class);
            accountPropertiesMap.put(
                "customResources[" + currentCrdIndex + "]", crdWrapper.getWrappedInstance());
            crdExist = true;
          }
          crdWrapper.setPropertyValue(
              match.group(6),
              entry.getValue()); // match.group(6) is a property of CustomKubernetesResource
        } else {
          if (propertyName.equals("kubeconfigFile")) {
            if (((String) value).startsWith("configserver:")) {
              value = propertiesMapExtractor.resolveConfigServerFilePath((String) value);
            }
          }
          accountPropertiesMap.put(
              entry.getKey().substring(entry.getKey().indexOf(propertyName)), value);
        }
      } else {
        log.debug("Ignoring the properties having blank values. Key:" + entry.getKey());
      }
    }
    // setting last account properties
    if (accountPropertiesMap != null) {
      wrapper.setPropertyValues(accountPropertiesMap);
    }
  }
}
