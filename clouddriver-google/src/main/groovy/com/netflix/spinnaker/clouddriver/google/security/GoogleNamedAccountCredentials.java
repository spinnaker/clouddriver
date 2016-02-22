/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.ComputeScopes;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.RegionList;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GoogleNamedAccountCredentials implements AccountCredentials<GoogleCredentials> {
    public GoogleNamedAccountCredentials(String accountName, String environment, String accountType, String projectName, String jsonKey, List<String> imageProjects, String applicationName) {
      this(accountName, environment, accountType, projectName, jsonKey, imageProjects, null, applicationName);
    }

    public GoogleNamedAccountCredentials(String accountName, String environment, String accountType, String projectName, String jsonKey, List<String> imageProjects, List<String> requiredGroupMembership, String applicationName) {
      this.accountName = accountName;
      this.environment = environment;
      this.accountType = accountType;
      this.projectName = projectName;
      this.jsonKey = jsonKey;
      this.imageProjects = imageProjects;
      this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
      this.applicationName = applicationName;
      this.credentials = (projectName != null) ? buildCredentials() : null;
      this.regionToZonesMap = (credentials != null && credentials.getCompute() != null) ? queryRegions(credentials.getCompute(), projectName) : Collections.emptyMap();
    }

    @Override
    public String getName() {
      return accountName;
    }

    @Override
    public String getEnvironment() {
      return environment;
    }

    @Override
    public String getAccountType() {
      return accountType;
    }

    @Override
    public String getCloudProvider() {
      return CLOUD_PROVIDER;
    }

    public Map<String, List<String>> getRegions() {
      return regionToZonesMap;
    }

    public GoogleCredentials getCredentials() {
      return credentials;
    }

    private HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
       return new HttpRequestInitializer() {
         @Override
         public void initialize(HttpRequest httpRequest) throws IOException {
           requestInitializer.initialize(httpRequest);
           httpRequest.setConnectTimeout(2 * 60000);  // 2 minutes connect timeout
           httpRequest.setReadTimeout(2 * 60000);  // 2 minutes read timeout
         }
       };
     }

    private GoogleCredentials buildCredentials() {
      JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
      HttpTransport httpTransport = buildHttpTransport();

      try {
        if (jsonKey != null) {
          try (InputStream credentialStream = new ByteArrayInputStream(jsonKey.getBytes())) {
            // JSON key was specified in matching config on key server.
            GoogleCredential credential = GoogleCredential.fromStream(credentialStream, httpTransport, jsonFactory).createScoped(Collections.singleton(ComputeScopes.COMPUTE));
            Compute compute = new Compute.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(setHttpTimeout(credential)).build();

            return new GoogleCredentials(projectName, compute, imageProjects);
          }
        } else {
          // No JSON key was specified in matching config on key server, so use application default credentials.
          GoogleCredential credential = GoogleCredential.getApplicationDefault();
          Compute compute = new Compute.Builder(httpTransport, jsonFactory, credential).setApplicationName(applicationName).build();

          return new GoogleCredentials(projectName, compute, imageProjects);
        }
      } catch (IOException ioe) {
        throw new RuntimeException("failed to create credentials", ioe);
      }
    }

    private HttpTransport buildHttpTransport() {
      try {
        return GoogleNetHttpTransport.newTrustedTransport();
      } catch (Exception e) {
        throw new RuntimeException("Failed to create trusted transport", e);
      }
    }

    private static Map<String, List<String>> queryRegions(Compute compute, String projectName) {
      RegionList regionList = fetchRegions(compute, projectName);
      return convertToMap(regionList);
    }

    @VisibleForTesting
    static Map<String, List<String>> convertToMap(RegionList regionList) {
      return regionList.getItems().stream()
        .collect(Collectors.toMap(
          Region::getName,
          r -> r.getZones().stream()
            .map(GoogleNamedAccountCredentials::getLocalName)
            .collect(Collectors.toList())));
    }

    private static RegionList fetchRegions(Compute compute, String projectName) {
      try {
        return compute.regions().list(projectName).execute();
      } catch (IOException ioe) {
        throw new RuntimeException("Failed loading regions for " + projectName, ioe);
      }
    }

    private static String getLocalName(String fullUrl) {
        return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
    }

    @Override
    public String getProvider() {
      return getCloudProvider();
    }

    public String getAccountName() {
      return accountName;
    }

    public String getProjectName() {
      return projectName;
    }

    public List<String> getImageProjects() {
      return imageProjects;
    }

    public List<String> getRequiredGroupMembership() {
      return requiredGroupMembership;
    }

    public String getApplicationName() {
      return applicationName;
    }

    private static final String CLOUD_PROVIDER = "gce";
    private final Map<String, List<String>> regionToZonesMap;
    private final String accountName;
    private final String environment;
    private final String accountType;
    private final String projectName;
    private final String jsonKey;
    private final List<String> imageProjects;
    private final GoogleCredentials credentials;
    private final List<String> requiredGroupMembership;
    private final String applicationName;
}
