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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.api

import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.exception.KubernetesClientOperationException
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesApiClientConfig
import io.kubernetes.client.ApiClient
import io.kubernetes.client.ApiException
import io.kubernetes.client.Configuration
import io.kubernetes.client.apis.AppsV1beta1Api
import io.kubernetes.client.models.*
import io.kubernetes.client.apis.ExtensionsV1beta1Api
import io.kubernetes.client.apis.AutoscalingV1Api
import io.kubernetes.client.apis.CoreV1Api

import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class KubernetesClientApiAdapter {

  String account

  static final int RETRY_COUNT = 20
  static final long RETRY_MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10)
  static final long RETRY_INITIAL_WAIT_MILLIS = 100
  static final int API_CALL_TIMEOUT_SECONDS = 60
  static final int TERMINATION_GRACE_PERIOD_SECONDS = 30
  static final String API_CALL_RESULT_FORMAT = ""
  static final int SHUTDOWN_ALL_PODS = 0
  static final String DEPLOYMENT_ANNOTATION = "deployment.kubernetes.io"
  final Registry spectatorRegistry
  final Clock spectatorClock
  final ApiClient client
  final AppsV1beta1Api apiInstance
  final ExtensionsV1beta1Api extApi
  final AutoscalingV1Api scalerApi
  final CoreV1Api coreApi

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
    apiInstance = new AppsV1beta1Api()
    extApi = new ExtensionsV1beta1Api()
    scalerApi = new AutoscalingV1Api()
    coreApi = new CoreV1Api()
  }

  KubernetesClientOperationException formatException(String operation, String namespace, ApiException e) {
    account ? new KubernetesClientOperationException(account, "$operation in $namespace", e) :
      new KubernetesClientOperationException("$operation in $namespace", e)
  }

  KubernetesClientOperationException formatException(String operation, ApiException e) {
    account ? new KubernetesClientOperationException(account, "$operation", e) :
      new KubernetesClientOperationException("$operation", e)
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
      try {
        /*
          FIXME: note this is k8s-client api issue and we are working this as workaround.
        */
        V1beta1StatefulSetList list = apiInstance.listNamespacedStatefulSet(namespace, null, null, null, true, null, null, null, API_CALL_TIMEOUT_SECONDS, false);
        String apiVersion = list.getApiVersion();
        for (V1beta1StatefulSet item : list.getItems()) {
          item.setApiVersion(apiVersion);
          item.setKind("StatefulSet");
        }

        return list.items
      } catch(Exception e){
        log.debug(e.message.toString())
      }
    }
  }

  List<V1beta1DaemonSet> getDaemonSets(String namespace) {
    exceptionWrapper("daemonSets.list", "Get Daemon Sets", namespace) {
      try {
        /*
       "fixme" and note this is k8s-client api issue and we are working this as workaround.
        */
        V1beta1DaemonSetList list = extApi.listNamespacedDaemonSet(namespace, null, null, null, null, null, null, null, API_CALL_TIMEOUT_SECONDS, false)
        String apiVersion = list.getApiVersion();
        for (V1beta1DaemonSet item : list.getItems()) {
          item.setApiVersion(apiVersion);
          item.setKind("DaemonSet");
        }

        return list.items
      } catch (ApiException ex) {
        log.debug(e.message.toString())
      }
    }
  }

  V1beta1StatefulSet createStatfulSet(String namespace, V1beta1StatefulSet statefulSet) {
    exceptionWrapper("statefulSets.create", "Create Stateful Set ${statefulSet?.metadata?.name}", namespace) {
      return apiInstance.createNamespacedStatefulSet(namespace, statefulSet, API_CALL_RESULT_FORMAT)
    }
  }

  V1beta1StatefulSet replaceStatfulSet(String name, String namespace, V1beta1StatefulSet statefulSet) {
    exceptionWrapper("statefulSets.replace", "Replace Stateful Set ${name}", namespace) {
      def deployedControllerSet = getStatefulSet(name, namespace)
      return apiInstance.replaceNamespacedStatefulSet(name, namespace, KubernetesClientApiConverter.toReplaceStatefulSet(statefulSet, deployedControllerSet), API_CALL_RESULT_FORMAT)
    }
  }

  V1beta1StatefulSet resizeStatefulSet(String name, String namespace, int targetSize) {
    exceptionWrapper("statefulSets.resize", "Resize Stateful Set $name", namespace) {
      KubernetesControllerPatch operation = new KubernetesControllerPatch()
      operation.op = "replace"
      operation.path = "/spec/replicas"
      operation.value = targetSize
      List<KubernetesControllerPatch> patch = new ArrayList<KubernetesControllerPatch>()
      patch.add(operation)

      V1beta1StatefulSet statefulSet = apiInstance.patchNamespacedStatefulSet(name, namespace, patch, null)
      def labels = statefulSet.metadata.labels
      V1PodList pods = getPods(namespace, labels)
      int podSize = pods.items.size()
      int terminationGracePeriodSeconds = statefulSet.spec.template.spec.terminationGracePeriodSeconds ?: TERMINATION_GRACE_PERIOD_SECONDS

      if (podSize != targetSize) {
        waitForNumberOfPodUpdated(namespace, labels, podSize, targetSize, terminationGracePeriodSeconds)
      }

      return statefulSet
    }
  }

  void waitForNumberOfPodUpdated(String namespace, Map<String, String> labels, int podSize, int targetSize, int terminationGracePeriodSeconds) {
    if (targetSize < podSize) {
      //Only wait if reduce pods
      System.sleep(terminationGracePeriodSeconds * 1000)
    }

    int delayTime = 5
    int delayInterval = 12
    while (podSize != targetSize) {
      def podlist = getPods(namespace, labels)
      try {
        podSize = podlist.items.size()
      } catch(Exception e) {
        log.debug(e.message)
        break
      }

      System.sleep( delayTime * 1000)
      if (delayInterval > 0) {
        delayInterval--
      } else {
        break
      }
    }
  }

  /*boolean hardDestroyStatefulSet(String name, String namespace) {
    exceptionWrapper("statefulSets.delete", "Hard Destroy Stateful Set $name", namespace) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions()
      Boolean orphanDependents = true

      V1Status status
      if (resizeStatefulSet(name, namespace, SHUTDOWN_ALL_PODS)) {
        try {
          status = apiInstance.deleteNamespacedStatefulSet(name, namespace, deleteOptions, null, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, null)
        } catch(Exception e) {
          log.debug(e.message)
        }
      }
      return (status?.status == "Success" ? true : false )
    }
  }

  V1beta1StatefulSet getStatefulSet(String statefulSetName, String namespace) {
    exceptionWrapper("statefulSets.create", "Get Stateful Set ${statefulSetName}", namespace) {
      try {
        return apiInstance.readNamespacedStatefulSet(statefulSetName, namespace, API_CALL_RESULT_FORMAT, null, null)
      } catch (Exception e) {
        log.debug(e.message)
      }

      return null
    }
  }

  V1beta1StatefulSet deleteStatefulSetPods(String name, String namespace) {
    exceptionWrapper("statefulSets.create", "Get Stateful Set ${name}", namespace) {
      return apiInstance.readNamespacedStatefulSet(name, namespace, API_CALL_RESULT_FORMAT, null, null)
    }
  }*/

  V1HorizontalPodAutoscaler getAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.get", "Get Autoscaler $name", namespace) {
      V1HorizontalPodAutoscaler result = null

      try {
        result = scalerApi.readNamespacedHorizontalPodAutoscalerStatus(name, namespace, API_CALL_RESULT_FORMAT)
      } catch (Exception ex) {
        log.info "Unable to find autoscaler {$name in $namespace}: $ex."
      }

      return result
    }
  }

  V1HorizontalPodAutoscaler createAutoscaler(String namespace, V1HorizontalPodAutoscaler autoscaler) {
    exceptionWrapper("horizontalPodAutoscalers.create", "Create Autoscaler ${autoscaler?.metadata?.name}", namespace) {
      return scalerApi.createNamespacedHorizontalPodAutoscaler(namespace, autoscaler, API_CALL_RESULT_FORMAT)
    }
  }

  V1PodList getPods(String namespace, Map<String, String> labels) {
    exceptionWrapper("pods.list", "Get Pods matching $labels", namespace) {
      String label
      if (labels != null) {
        Map.Entry<String, String> entry = labels.entrySet().iterator().next()
        String key = entry.getKey()
        String value = entry.getValue()
        label = key + "=" + value
      }
      coreApi.listNamespacedPod(namespace, null, null, null, false, label, null, null, API_CALL_TIMEOUT_SECONDS, false)
    }
  }

  boolean deleteAutoscaler(String namespace, String name) {
    exceptionWrapper("horizontalPodAutoscalers.delete", "Destroy Autoscaler $name", namespace) {
      V1DeleteOptions deleteOption = new V1DeleteOptions()
      Boolean orphanDependents = true
      String propagationPolicy = ""

      return scalerApi.deleteNamespacedHorizontalPodAutoscaler(name, namespace, deleteOption, API_CALL_RESULT_FORMAT, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, propagationPolicy);
    }
  }

  V1beta1DaemonSet createDaemonSet(String namespace, V1beta1DaemonSet daemonSet) {
    exceptionWrapper("DaemonSet.create", "Create Daemon Set ${daemonSet?.metadata?.name}", namespace) {
      return extApi.createNamespacedDaemonSet(namespace, daemonSet, API_CALL_RESULT_FORMAT)
    }
  }

  /*V1beta1DaemonSet deleteDaemonSetPod(String name, String namespace, V1beta1DaemonSet deployedControllerSet) {
    exceptionWrapper("statefulSets.create", "Replace Daemon Set ${name}", namespace) {
      def nodeSelector = new HashMap<String, String>()
      UUID uuid = UUID.randomUUID()

      nodeSelector.put(uuid.toString(), uuid.toString())
      deployedControllerSet.spec.template.spec.nodeSelector = nodeSelector

      V1beta1DaemonSet daemonSet = extApi.replaceNamespacedDaemonSet(name, namespace, deployedControllerSet, null)
      int terminationGracePeriodSeconds = daemonSet.spec.template.spec.terminationGracePeriodSeconds ?: TERMINATION_GRACE_PERIOD_SECONDS

      waitForNumberOfPodUpdated(namespace, daemonSet.metadata.labels, 1, SHUTDOWN_ALL_PODS, terminationGracePeriodSeconds)

      return daemonSet
    }
  }*/

  V1beta1DaemonSet replaceDaemonSet(String name, String namespace, V1beta1DaemonSet daemonSet) {
    exceptionWrapper("DaemonSet.replace", "Replace Daemon Set ${name}", namespace) {
      def deployedControllerSet = getDaemonSet(name, namespace)
      return extApi.replaceNamespacedDaemonSet(name, namespace, KubernetesClientApiConverter.toReplaceDaemonSet(daemonSet, deployedControllerSet), API_CALL_RESULT_FORMAT)
    }
  }

  V1beta1DaemonSet getDaemonSet(String name, String namespace) {
    exceptionWrapper("DaemonSet.get", "Get Daemon Set ${name}", namespace) {
      try {
        return extApi.readNamespacedDaemonSet(name, namespace, API_CALL_RESULT_FORMAT, true, false)
      } catch(Exception e) {
        LOG.info(e.message)
      }
      return null
    }
  }

  List<String> getNamespacesByName() {
    exceptionWrapper("namespaces.list", "Get Namespaces", null) {
      V1NamespaceList result = coreApi.listNamespace(API_CALL_RESULT_FORMAT, null, null, null, null, null, null, 30, null)
      return result.items.collect { n -> n.getMetadata().getName() }
    }
  }

  /*boolean hardDestroyDaemonSet(String name, String namespace) {
    exceptionWrapper("daemonSets.delete", "Hard Destroy Daemon Set ${name}", namespace) {
      V1DeleteOptions body = new V1DeleteOptions()
      Boolean orphanDependents = true
      def deployedControllerSet = getDaemonSet(name, namespace)
      V1beta1DaemonSet daemonset = deleteDaemonSetPod(name, namespace, deployedControllerSet)
      //V1Status status = coreApi.deleteNamespacedPod(name, namespace, body, null, gracePeriodSeconds, orphanDependents, null);
      V1Status status
      try {
        status = extApi.deleteNamespacedDaemonSet(name, namespace, body, null, TERMINATION_GRACE_PERIOD_SECONDS, orphanDependents, null);
      } catch(Exception e) {
        LOG.debug(e.message)
      }
        return (status?.status == "Success" ? true : false )
    }
  }*/

  /*
  Reference Kubernetes patch operation spec - https://github.com/kubernetes/community/blob/master/contributors/devel/api-conventions.md#patch-operations
  Example,
    [
     { "op": "test", "path": "/a/b/c", "value": "foo" },
     { "op": "remove", "path": "/a/b/c" },
     { "op": "add", "path": "/a/b/c", "value": [ "foo", "bar" ] },
     { "op": "replace", "path": "/a/b/c", "value": 42 },
     { "op": "move", "from": "/a/b/c", "path": "/a/b/d" },
     { "op": "copy", "from": "/a/b/d", "path": "/a/b/e" }
   ]
  */
  class KubernetesControllerPatch {
    String op
    String path
    Object value

    KubernetesControllerPatch() {}
  }
}

