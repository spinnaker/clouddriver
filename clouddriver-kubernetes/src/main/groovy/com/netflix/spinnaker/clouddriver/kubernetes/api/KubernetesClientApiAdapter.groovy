/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.api

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesApiClientConfig
import io.kubernetes.client.ApiClient
import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.AppsV1beta1Api
import io.kubernetes.client.models.*

import java.util.concurrent.TimeUnit

class KubernetesClientApiAdapter {
  String account

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100
  static final int API_CALL_TIMEOUT_SECONDS = 60
  static final String DEPLOYMENT_ANNOTATION = "deployment.kubernetes.io"
  final Registry spectatorRegistry
  final Clock spectatorClock
  final ApiClient client
  final AppsV1beta1Api apiInstance

  public spectatorRegistry() { return spectatorRegistry }

  KubernetesClientApiAdapter(String account, KubernetesApiClientConfig config, Registry spectatorRegistry) {
    if (!config) {
      throw new IllegalArgumentException("Config may not be null.")
    }

    this.account = account
    this.spectatorRegistry = spectatorRegistry
    this.spectatorClock = spectatorRegistry.clock()

    client = config.getApiCient()
    Configuration.setDefaultApiClient(client)
    apiInstance = new AppsV1beta1Api();
  }

  KubernetesOperationException formatException(String operation, String namespace, ApiException e) {
    account ? new KubernetesOperationException(account, "$operation in $namespace", e) :
      new KubernetesOperationException("$operation in $namespace", e)
  }

  KubernetesOperationException formatException(String operation, ApiException e) {
    account ? new KubernetesOperationException(account, "$operation", e) :
      new KubernetesOperationException("$operation", e)
  }

  Boolean blockUntilResourceConsistent(Object desired, Closure<Long> getGeneration, Closure getResource) {
    def current = getResource()

    def wait = RETRY_INITIAL_WAIT_MILLIS
    def attempts = 0
    while (getGeneration(current) < getGeneration(desired)) {
      attempts += 1
      if (attempts > RETRY_COUNT) {
        return false
      }

      sleep(wait)
      wait = [wait * 2, RETRY_MAX_WAIT_MILLIS].min()

      current = getResource()
    }

    return true
  }

  private <T> T exceptionWrapper(String methodName, String operationMessage, String namespace, Closure<T> doOperation) {
    T result = null
    Exception failure
    long startTime = spectatorClock.monotonicTime()

    try {
      result = doOperation()
    } catch (ApiException e) {
      if (namespace) {
        failure = formatException(operationMessage, namespace, e)
      } else {
        failure = formatException(operationMessage, e)
      }
    } catch (Exception e) {
      failure = e
    } finally {

      def tags = ["method": methodName,
                  "account": account,
                  "namespace" : namespace ? namespace : "none",
                  "success": failure ? "false": "true"]
      if (failure) {
        tags["reason"] = failure.class.simpleName
      }

      spectatorRegistry.timer(
        spectatorRegistry.createId("kubernetes.api", tags))
        .record(spectatorClock.monotonicTime() - startTime, TimeUnit.NANOSECONDS)

      if (failure) {
        throw failure
      } else {
        return result
      }
    }
  }

  List<V1beta1StatefulSet> getStatefulSets(String namespace) {
    exceptionWrapper("statefulSets.list", "Get Stateful Sets", namespace) {
      V1beta1StatefulSetList list = apiInstance.listNamespacedStatefulSet(namespace, null, null, null, null, API_CALL_TIMEOUT_SECONDS, false)
      List<V1beta1StatefulSet> statefulSets = new ArrayList<V1beta1StatefulSet>()
      list.items?.forEach({ item ->
        statefulSets.add(item)
      })
      return statefulSets
    }
  }
}
