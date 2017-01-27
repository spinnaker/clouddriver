/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.api.services.appengine.v1.Appengine
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppEngineGitCredentialType
import com.netflix.spinnaker.clouddriver.appengine.gitClient.AppEngineGitCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.TupleConstructor

@TupleConstructor
class AppEngineNamedAccountCredentials implements AccountCredentials<AppEngineCredentials> {
  final String name
  final String environment
  final String accountType
  final String project
  final String cloudProvider = AppEngineCloudProvider.ID
  final String region
  final List<String> regions
  final List<String> requiredGroupMembership

  @JsonIgnore
  final String jsonPath
  final AppEngineCredentials credentials
  final String applicationName
  @JsonIgnore
  final Appengine appengine
  @JsonIgnore
  final String serviceAccountEmail
  @JsonIgnore
  final String localRepositoryDirectory
  @JsonIgnore
  final AppEngineGitCredentials gitCredentials
  final List<AppEngineGitCredentialType> supportedGitCredentialTypes

  static class Builder {
    String name
    String environment
    String accountType
    String project
    String region
    List<String> requiredGroupMembership
    AppEngineCredentials credentials

    String jsonKey
    String jsonPath
    String applicationName
    Appengine appengine
    String serviceAccountEmail
    String localRepositoryDirectory
    String gitHttpsUsername
    String gitHttpsPassword
    String githubOAuthAccessToken
    String sshPrivateKeyFilePath
    String sshPrivateKeyPassword
    AppEngineGitCredentials gitCredentials

    /*
    * If true, the builder will overwrite region with a value from the platform.
    * */
    Boolean liveLookupsEnabled = true

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder project(String project) {
      this.project = project
      return this
    }

    Builder region(String region) {
      this.region = region
      this.liveLookupsEnabled = false
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder jsonPath(String jsonPath) {
      this.jsonPath = jsonPath
      return this
    }

    Builder jsonKey(String jsonKey) {
      this.jsonKey = jsonKey
      return this
    }

    Builder applicationName(String applicationName) {
      this.applicationName = applicationName
      return this
    }

    Builder credentials(AppEngineCredentials credentials) {
      this.credentials = credentials
      return this
    }

    Builder appengine(Appengine appengine) {
      this.appengine = appengine
      return this
    }

    Builder serviceAccountEmail(String serviceAccountEmail) {
      this.serviceAccountEmail = serviceAccountEmail
      return this
    }

    Builder localRepositoryDirectory(String localRepositoryDirectory) {
      this.localRepositoryDirectory = localRepositoryDirectory
      return this
    }

    Builder gitHttpsUsername(String gitHttpsUsername) {
      this.gitHttpsUsername = gitHttpsUsername
      return this
    }

    Builder gitHttpsPassword(String gitHttpsPassword) {
      this.gitHttpsPassword = gitHttpsPassword
      return this
    }

    Builder githubOAuthAccessToken(String githubOAuthAccessToken) {
      this.githubOAuthAccessToken = githubOAuthAccessToken
      return this
    }

    Builder sshPrivateKeyFilePath(String sshPrivateKeyFilePath) {
      this.sshPrivateKeyFilePath = sshPrivateKeyFilePath
      return this
    }

    Builder sshPrivateKeyPassword(String sshPrivateKeyPassword) {
      this.sshPrivateKeyPassword = sshPrivateKeyPassword
      return this
    }

    Builder gitCredentials(AppEngineGitCredentials gitCredentials) {
      this.gitCredentials = gitCredentials
      return this
    }

    AppEngineNamedAccountCredentials build() {
      credentials = credentials ?:
        jsonKey ?
        new AppEngineJsonCredentials(project, jsonKey) :
        new AppEngineCredentials(project)

      appengine = appengine ?: credentials.getAppEngine(applicationName)

      if (liveLookupsEnabled) {
        region = appengine.apps().get(project).execute().getLocationId()
      }

      gitCredentials = gitCredentials ?: new AppEngineGitCredentials(
        gitHttpsUsername,
        gitHttpsPassword,
        githubOAuthAccessToken,
        sshPrivateKeyFilePath,
        sshPrivateKeyPassword
      )

      return new AppEngineNamedAccountCredentials(name,
                                                  environment,
                                                  accountType,
                                                  project,
                                                  AppEngineCloudProvider.ID,
                                                  region,
                                                  [region],
                                                  requiredGroupMembership,
                                                  jsonPath,
                                                  credentials,
                                                  applicationName,
                                                  appengine,
                                                  serviceAccountEmail,
                                                  localRepositoryDirectory,
                                                  gitCredentials,
                                                  gitCredentials.getSupportedCredentialTypes())
    }
  }
}
