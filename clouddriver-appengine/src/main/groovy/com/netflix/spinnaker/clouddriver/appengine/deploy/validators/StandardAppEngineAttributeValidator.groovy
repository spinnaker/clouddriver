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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineIllegalArgumentExeception
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineResourceNotFoundException
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineInstanceProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.validation.Errors

class StandardAppEngineAttributeValidator {
  static final namePattern = /^[a-z0-9]+([-a-z0-9]*[a-z0-9])?$/
  static final prefixPattern = /^[a-z0-9]+$/

  String context
  Errors errors

  StandardAppEngineAttributeValidator(String context, Errors errors) {
    this.context = context
    this.errors = errors
  }

  def validateCredentials(String credentials, AccountCredentialsProvider accountCredentialsProvider) {
    def result = validateNotEmpty(credentials, "account")
    if (result) {
      def appEngineCredentials = accountCredentialsProvider.getCredentials(credentials)
      if (!(appEngineCredentials?.credentials instanceof AppEngineCredentials)) {
        errors.rejectValue("${context}.account",  "${context}.account.notFound")
        result = false
      }
    }
    result
  }

  def validateNotEmpty(Object value, String attribute) {
    if (value != "" && value != null && value != []) {
      return true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
      return false
    }
  }

  def validateApplication(String value, String attribute) {
    if (validateNotEmpty(value, attribute)) {
      return validateByRegex(value, attribute, prefixPattern)
    } else {
      return false
    }
  }

  def validateStack(String value, String attribute) {
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, prefixPattern)
    }
  }

  def validateDetails(String value, String attribute) {
    if (!value) {
      return true
    } else {
      return validateByRegex(value, attribute, namePattern)
    }
  }

  def validateByRegex(String value, String attribute, String regex) {
    if (value ==~ regex) {
      return true
    } else {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Must match ${regex})")
      return false
    }
  }

  def validateTrafficSplit(AppEngineTrafficSplit trafficSplit, String attribute) {
    if (validateNotEmpty(trafficSplit, attribute)) {
      if (trafficSplit.allocations) {
        return validateAllocations(trafficSplit.allocations, attribute + ".allocations")
      } else if (trafficSplit.shardBy) {
        return true
      } else {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.empty")
        return false
      }
    } else {
      return false
    }
  }

  def validateAllocations(Map<String, Double> allocations, String attribute) {
    if (allocations.collect { k, v -> v }.sum() != 1) {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Allocations must sum to 1)")
      return false
    } else {
      return true
    }
  }

  def validateServerGroupsCanBeEnabled(Collection<String> serverGroupNames,
                                       String loadBalancerName,
                                       AppEngineNamedAccountCredentials credentials,
                                       AppEngineClusterProvider appEngineClusterProvider,
                                       String attribute) {
    def rejectedServerGroups = serverGroupNames.inject([:].withDefault { [] }, { Map reject, serverGroupName ->
      def serverGroup = appEngineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
      if (!serverGroup) {
        reject.notFound << serverGroupName
        return reject
      } else if (loadBalancerName && serverGroup?.loadBalancers[0] != loadBalancerName) {
        reject.notRegisteredWithLoadBalancer << serverGroupName
        return reject
      } else {
        return reject
      }
    })

    def valid = true
    if (rejectedServerGroups.notFound) {
      def notFound = rejectedServerGroups.notFound
      errors.rejectValue(
        "${context}.${attribute}",
        "${context}.${attribute}.invalid (Server group${notFound.size() > 1 ? "s" : ""} ${notFound.join(", ")} not found)."
      )

      valid = false
    }

    if (rejectedServerGroups.notRegisteredWithLoadBalancer) {
      def notRegistered = rejectedServerGroups.notRegisteredWithLoadBalancer
      errors.rejectValue(
        "${context}.${attribute}",
        "${context}.${attribute}.invalid (Server group${notRegistered.size() > 1 ? "s" : ""} ${notRegistered.join(", ")} " +
          "not registered with load balancer $loadBalancerName)."
      )

      valid = false
    }

    return valid
  }

  def validateLoadBalancerCanBeDeleted(String loadBalancerName, String attribute) {
    if (loadBalancerName == "default") {
      errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Cannot delete default service).")
      return false
    } else {
      return true
    }
  }

  def validateInstances(List<String> instanceIds,
                        AppEngineNamedAccountCredentials credentials,
                        AppEngineInstanceProvider appEngineInstanceProvider,
                        String attribute) {
    def instances = instanceIds.collect { appEngineInstanceProvider.getInstance(credentials.name, credentials.region, it) }
    def valid = true

    instances.eachWithIndex { AppEngineInstance instance, int i ->
      def name = instanceIds[i]
      if (!instance) {
        errors.rejectValue("${context}.${attribute}", "${context}.${attribute}.invalid (Instance $name not found).")
        valid = false
        return null
      }

      if (!instance.serverGroup) {
        errors.rejectValue("${context}.${attribute}",
                           "${context}.${attribute}.invalid (Could not find parent server group for instance $name).")
        valid = false
      }

      if (!instance.loadBalancers?.getAt(0)) {
        errors.rejectValue("${context}.${attribute}",
                           "${context}.${attribute}.invalid (Could not find parent load balancer for instance $name).")
        valid = false
      }
    }

    return valid
  }

  def validateServerGroupCanBeDisabled(String serverGroupName,
                                       AppEngineNamedAccountCredentials credentials,
                                       AppEngineClusterProvider clusterProvider,
                                       AppEngineLoadBalancerProvider loadBalancerProvider,
                                       String attribute) {
    def serverGroup = clusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def valid = true
    if (!serverGroup) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Server group $serverGroupName not found).")
      valid = false
      return valid
    }

    def loadBalancerName = serverGroup?.loadBalancers?.first()
    def loadBalancer = loadBalancerProvider.getLoadBalancer(credentials.name, loadBalancerName)
    if (!loadBalancer) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Could not find parent load balancer $loadBalancerName for server group $serverGroupName).")
      valid = false
      return valid
    }

    if (loadBalancer?.split?.allocations?.get(serverGroupName) == 1 as Double) {
      errors.rejectValue("${context}.${attribute}",
                         "${context}.${attribute}.invalid (Server group $serverGroupName is the only server group " +
                         "receiving traffic from load balancer $loadBalancerName).")
      valid = false
    }
    return valid
  }
}
