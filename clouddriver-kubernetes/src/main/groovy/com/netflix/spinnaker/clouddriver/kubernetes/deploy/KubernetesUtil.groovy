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

package com.netflix.spinnaker.clouddriver.kubernetes.deploy

import com.netflix.frigga.NameValidation
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesImageDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.exception.KubernetesIllegalArgumentException
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import org.springframework.beans.factory.annotation.Value

class KubernetesUtil {
  static String SECURITY_GROUP_LABEL_PREFIX = "security-group-"
  static String LOAD_BALANCER_LABEL_PREFIX = "load-balancer-"
  static String REPLICATION_CONTROLLER_LABEL = "replication-controller"
  @Value("kubernetes.defaultRegistry:gcr.io")
  static String DEFAULT_REGISTRY
  private static int SECURITY_GROUP_LABEL_PREFIX_LENGTH = SECURITY_GROUP_LABEL_PREFIX.length()
  private static int LOAD_BALANCER_LABEL_PREFIX_LENGTH = LOAD_BALANCER_LABEL_PREFIX.length()

  static String getNextSequence(String clusterName, String namespace, KubernetesCredentials credentials) {
    def maxSeqNumber = -1
    def replicationControllers = credentials.apiAdaptor.getReplicationControllers(namespace)

    replicationControllers.forEach( { replicationController ->
      def names = Names.parseName(replicationController.getMetadata().getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    })

    String.format("%03d", ++maxSeqNumber)
  }

  static List<String> getImagePullSecrets(ReplicationController rc) {
    rc.spec?.template?.spec?.imagePullSecrets?.collect({ it.name })
  }

  static KubernetesImageDescription buildImageDescription(String image) {
    def sIndex = image.indexOf('/')
    def result = new KubernetesImageDescription()

    // No slash means we only provided a repository name & optional tag.
    if (sIndex < 0) {
      result.repository = image
    } else {
      def sPrefix = image.substring(0, sIndex)

      // Check if the content before the slash is a registry (either localhost, or a URL)
      if (sPrefix.startsWith('localhost') || sPrefix.contains('.')) {
        result.registry = sPrefix

        image = image.substring(sIndex + 1)
      }
    }

    def cIndex = image.indexOf(':')

    if (cIndex < 0) {
      result.repository = image
    } else {
      result.tag = image.substring(cIndex + 1)
      result.repository = image.subSequence(0, cIndex)
    }

    normalizeImageDescription(result)
    result
  }

  static Void normalizeImageDescription(KubernetesImageDescription image) {
    if (!image.registry) {
      image.registry = DEFAULT_REGISTRY
    }

    if (!image.tag) {
      image.tag = "latest"
    }

    if (!image.repository) {
      throw new IllegalArgumentException("Image descriptions must provide a repository.")
    }
  }

  static String getImageId(KubernetesImageDescription image) {
    return getImageId(image.registry, image.repository, image.tag)
  }

  static String getImageId(String registry, String repository, String tag) {
    "$registry/$repository:$tag"
  }

  static String validateNamespace(KubernetesCredentials credentials, String namespace) {
    def resolvedNamespace = namespace ?: "default"
    if (!credentials.isRegisteredNamespace(resolvedNamespace)) {
      def error = "Registered namespaces are ${credentials.getNamespaces()}."
      if (namespace) {
        error = "Namespace '$namespace' was not registered with provided credentials. $error"
      } else {
        error = "No provided namespace assumed to mean 'default' was not registered with provided credentials. $error"
      }
      throw new KubernetesIllegalArgumentException(error)
    }
    return resolvedNamespace
  }

  static List<String> getPodLoadBalancers(Pod pod) {
    def loadBalancers = []
    pod.metadata?.labels?.each { key, val ->
      if (isLoadBalancerLabel(key)) {
        loadBalancers.push(key.substring(LOAD_BALANCER_LABEL_PREFIX_LENGTH, key.length()))
      }
    }
    return loadBalancers
  }

  static List<String> getDescriptionLoadBalancers(ReplicationController rc) {
    def loadBalancers = []
    rc.spec?.template?.metadata?.labels?.each { key, val ->
      if (isLoadBalancerLabel(key)) {
        loadBalancers.push(key.substring(LOAD_BALANCER_LABEL_PREFIX_LENGTH, key.length()))
      }
    }
    return loadBalancers
  }

  static Boolean isLoadBalancerLabel(String key) {
    key.startsWith(LOAD_BALANCER_LABEL_PREFIX)
  }

  static List<String> getDescriptionSecurityGroups(ReplicationController rc) {
    def securityGroups = []
    rc.spec?.template?.metadata?.labels?.each { key, val ->
      if (key.startsWith(SECURITY_GROUP_LABEL_PREFIX)) {
        securityGroups.push(key.substring(SECURITY_GROUP_LABEL_PREFIX_LENGTH, key.length()))
      }
    }
    return securityGroups
  }

  static String securityGroupKey(String securityGroup) {
    return String.format("$SECURITY_GROUP_LABEL_PREFIX%s", securityGroup)
  }

  static String loadBalancerKey(String loadBalancer) {
    return String.format("$LOAD_BALANCER_LABEL_PREFIX%s", loadBalancer)
  }

  static String combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }
}
