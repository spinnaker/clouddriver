/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.aws.deploy.userdata

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataProvider
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.kork.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException

class LocalFileUserDataProvider implements UserDataProvider {
  private static final INSERTION_MARKER = '\nexport EC2_REGION='

  @VisibleForTesting LocalFileUserDataProperties localFileUserDataProperties
  @VisibleForTesting Front50Service front50Service
  @VisibleForTesting DefaultUserDataTokenizer defaultUserDataTokenizer

  @VisibleForTesting
  LocalFileUserDataProvider(){}

  LocalFileUserDataProvider(LocalFileUserDataProperties localFileUserDataProperties,
                            Front50Service front50Service,
                            DefaultUserDataTokenizer defaultUserDataTokenizer) {
    this.localFileUserDataProperties = localFileUserDataProperties
    this.front50Service = front50Service
    this.defaultUserDataTokenizer = defaultUserDataTokenizer
  }


  boolean isLegacyUdf(String account, String applicationName) {
    Closure<Boolean> result = {
      try {
        Map application = front50Service.getApplication(applicationName)
        if (application.legacyUdf == null) {
          return localFileUserDataProperties.defaultLegacyUdf
        }
        return Boolean.valueOf(application.legacyUdf)
      } catch (NotFoundException e) {
        return localFileUserDataProperties.defaultLegacyUdf
      } catch (SpinnakerServerException e) {
        throw e
      }
    }

    // TODO(rz) standardize retry logic
    final int maxRetry = 5
    final int retryBackoff = 500
    final Set<Integer> retryStatus = [429, 500]
    for (int i = 0; i < maxRetry; i++) {
      try {
        return result.call()
      } catch (SpinnakerHttpException e) {
        if (retryStatus.contains(e.getResponse().getStatus())) {
          Thread.sleep(retryBackoff)
        }
      } catch (SpinnakerNetworkException e) {
        Thread.sleep(retryBackoff)
      } catch (SpinnakerServerException e) {}
    }
    throw new IllegalStateException("Failed to read legacyUdf preference from front50 for $account/$applicationName")
  }

  @Override
  String getUserData(UserDataInput userDataInput) {
    def names = Names.parseName(userDataInput.asgName)
    boolean useLegacyUdf = userDataInput.legacyUdf != null ? userDataInput.legacyUdf : isLegacyUdf(userDataInput.account, names.app)
    def rawUserData = assembleUserData(useLegacyUdf, names, userDataInput.region, userDataInput.account)
    String userData = defaultUserDataTokenizer.replaceTokens(names, userDataInput, rawUserData, useLegacyUdf)
    return addAdditionalEnvVars(names, userData)
  }

  String assembleUserData(boolean legacyUdf, Names names, String region, String account) {
    def udfRoot = localFileUserDataProperties.udfRoot + (legacyUdf ? '/legacy' : '')

    String cluster = names.cluster
    String stack = names.stack

    // If app and group names are identical, only include their UDF file once.

    // LinkedHashSet ensures correct order and no duplicates when the app, cluster, and groupName are equal.
    Set<String> udfPaths = new LinkedHashSet<String>()
    udfPaths << "${udfRoot}/udf0"
    if (legacyUdf) {
      udfPaths << "${udfRoot}/udf-${account}"
      udfPaths << "${udfRoot}/udf-${region}-${account}"
      udfPaths << "${udfRoot}/udf1"
      udfPaths << "${udfRoot}/custom.d/${names.app}-${account}"
      udfPaths << "${udfRoot}/custom.d/${names.app}-${stack}-${account}"
      udfPaths << "${udfRoot}/custom.d/${cluster}-${account}"
      udfPaths << "${udfRoot}/custom.d/${names.group}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.app}-${stack}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${cluster}-${account}"
      udfPaths << "${udfRoot}/custom.region.d/${region}/${names.group}-${account}"
      udfPaths << "${udfRoot}/udf2"
    }

    // Concat all the Unix shell templates into one string
    udfPaths.collect { String path -> getContents(path) }.join('')
  }

  private static String addAdditionalEnvVars(Names names, String userData) {
    List<String> additionalEnvVars = []
    additionalEnvVars << names.countries ? "NETFLIX_COUNTRIES=${names.countries}" : null
    additionalEnvVars << names.devPhase ? "NETFLIX_DEV_PHASE=${names.devPhase}" : null
    additionalEnvVars << names.hardware ? "NETFLIX_HARDWARE=${names.hardware}" : null
    additionalEnvVars << names.partners ? "NETFLIX_PARTNERS=${names.partners}" : null
    additionalEnvVars << names.revision ? "NETFLIX_REVISION=${names.revision}" : null
    additionalEnvVars << names.usedBy ? "NETFLIX_USED_BY=${names.usedBy}" : null
    additionalEnvVars << names.redBlackSwap ? "NETFLIX_RED_BLACK_SWAP=${names.redBlackSwap}" : null
    additionalEnvVars << names.zone ? "NETFLIX_ZONE=${names.zone}" : null
    additionalEnvVars.removeAll([null])

    if (additionalEnvVars) {
      String insertion = "\n${additionalEnvVars.join('\n')}"
      userData = userData.replace(INSERTION_MARKER, "\n${insertion}${INSERTION_MARKER}")
    }
    return userData
  }

  private String getContents(String filePath) {
    try {
      File file = new File(filePath)
      String contents = file.getText('UTF-8')
      if (contents.length() && !contents.endsWith("\n")) {
        contents = contents + '\n'
      }
      return contents
    } catch (IOException ignore) {
      // This normal case happens if the requested file is not found.
      return ''
    }
  }

}
