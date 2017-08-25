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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesAwsElasticBlockStoreVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesCapabilities
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesConfigMapVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesContainerPort
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesEmptyDir
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesHandlerType
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesHostPath
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesKeyToPath
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesLifecycle
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesPersistentVolumeClaim
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesProbe
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesPullPolicy
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesResourceDescription
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesSeLinuxOptions
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesSecretVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesSecurityContext
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesStorageMediumType
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesVolumeMount
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesVolumeSource
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesVolumeSourceType
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesExecAction
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesHttpGetAction
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KeyValuePair
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.description.servergroup.KubernetesTcpSocketAction
import io.kubernetes.client.models.V1KeyToPath
import io.kubernetes.client.models.V1Container
import io.kubernetes.client.models.V1Probe
import io.kubernetes.client.models.V1Volume
import io.kubernetes.client.models.V1beta1StatefulSet
import io.kubernetes.client.models.V1beta1DaemonSet
import io.kubernetes.client.models.V1Handler
import io.kubernetes.client.models.V1ExecAction
import io.kubernetes.client.models.V1TCPSocketAction
import io.kubernetes.client.models.V1HTTPGetAction

/**
 * Created by spinnaker on 20/8/17.
 */
class KubernetesClientApiConverter {

  static DeployKubernetesAtomicOperationDescription fromStatefulSet(V1beta1StatefulSet statefulSet) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(statefulSet?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    /**
     * Will fetch this values in next Part of checkin , when required
     */
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(statefulSet.spec?.template?.metadata?.labels ?: [:])
    deployDescription.namespace = statefulSet?.metadata?.namespace
    deployDescription.targetSize = statefulSet?.spec?.replicas
    deployDescription.securityGroups = []
    deployDescription.replicaSetAnnotations = statefulSet?.metadata?.annotations
    deployDescription.podAnnotations = statefulSet?.spec?.template?.metadata?.annotations
    deployDescription.volumeClaims = statefulSet?.spec?.getVolumeClaimTemplates()
    deployDescription.volumeSources = statefulSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []
    deployDescription.hostNetwork = statefulSet?.spec?.template?.spec?.hostNetwork
    deployDescription.containers = statefulSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []
    deployDescription.terminationGracePeriodSeconds = statefulSet?.spec?.template?.spec?.terminationGracePeriodSeconds
    deployDescription.serviceAccountName = statefulSet?.spec?.template?.spec?.serviceAccountName
    deployDescription.nodeSelector = statefulSet?.spec?.template?.spec?.nodeSelector

    return deployDescription
  }

  static DeployKubernetesAtomicOperationDescription fromDaemonSet(V1beta1DaemonSet daemonSet) {
    def deployDescription = new DeployKubernetesAtomicOperationDescription()
    def parsedName = Names.parseName(daemonSet?.metadata?.name)

    deployDescription.application = parsedName?.app
    deployDescription.stack = parsedName?.stack
    deployDescription.freeFormDetails = parsedName?.detail
    deployDescription.loadBalancers = KubernetesUtil?.getLoadBalancers(daemonSet.spec?.template?.metadata?.labels ?: [:])
    deployDescription.namespace = daemonSet?.metadata?.namespace
    deployDescription.securityGroups = []
    deployDescription.podAnnotations = daemonSet?.spec?.template?.metadata?.annotations
    deployDescription.volumeSources = daemonSet?.spec?.template?.spec?.volumes?.collect {
      fromVolume(it)
    } ?: []

    deployDescription.hostNetwork = daemonSet?.spec?.template?.spec?.hostNetwork

    deployDescription.containers = daemonSet?.spec?.template?.spec?.containers?.collect {
      fromContainer(it)
    } ?: []

    deployDescription.terminationGracePeriodSeconds = daemonSet?.spec?.template?.spec?.terminationGracePeriodSeconds

    deployDescription.nodeSelector = daemonSet?.spec?.template?.spec?.nodeSelector

    return deployDescription
  }

  static KubernetesContainerDescription fromContainer(V1Container container) {
    if (!container) {
      return null
    }

    def containerDescription = new KubernetesContainerDescription()
    containerDescription.name = container.name
    containerDescription.imageDescription = KubernetesUtil.buildImageDescription(container.image)

    if (container.imagePullPolicy) {
      containerDescription.imagePullPolicy = KubernetesPullPolicy.valueOf(container.imagePullPolicy)
    }

    container.resources?.with {
      containerDescription.limits = limits?.cpu || limits?.memory ?
        new KubernetesResourceDescription(
          cpu: limits?.cpu,
          memory: limits?.memory
        ) : null

      containerDescription.requests = requests?.cpu || requests?.memory ?
        new KubernetesResourceDescription(
          cpu: requests?.cpu,
          memory: requests?.memory
        ) : null
    }

    if (container.lifecycle) {
      containerDescription.lifecycle = new KubernetesLifecycle()
      if (container.lifecycle.postStart) {
        containerDescription.lifecycle.postStart = fromHandler(container.lifecycle.postStart)
      }
      if (container.lifecycle.preStop) {
        containerDescription.lifecycle.preStop = fromHandler(container.lifecycle.preStop)
      }
    }

    containerDescription.ports = container.ports?.collect {
      def port = new KubernetesContainerPort()
      port.hostIp = it?.hostIP
      if (it?.hostPort) {
        port.hostPort = it?.hostPort?.intValue()
      }
      if (it?.containerPort) {
        port.containerPort = it?.containerPort?.intValue()
      }
      port.name = it?.name
      port.protocol = it?.protocol

      return port
    }

    if (container.securityContext) {
      def securityContext = container.securityContext

      containerDescription.securityContext = new KubernetesSecurityContext(privileged: securityContext.privileged,
        runAsNonRoot: securityContext.runAsNonRoot,
        runAsUser: securityContext.runAsUser,
        readOnlyRootFilesystem: securityContext.readOnlyRootFilesystem
      )

      if (securityContext.capabilities) {
        def capabilities = securityContext.capabilities

        containerDescription.securityContext.capabilities = new KubernetesCapabilities(add: capabilities.add, drop: capabilities.drop)
      }

      if (securityContext.seLinuxOptions) {
        def seLinuxOptions = securityContext.seLinuxOptions

        containerDescription.securityContext.seLinuxOptions = new KubernetesSeLinuxOptions(user: seLinuxOptions.user,
          role: seLinuxOptions.role,
          type: seLinuxOptions.type,
          level: seLinuxOptions.level
        )
      }
    }

    containerDescription.livenessProbe = fromV1Probe(container?.livenessProbe)
    containerDescription.readinessProbe = fromV1Probe(container?.readinessProbe)

    containerDescription.volumeMounts = container?.volumeMounts?.collect { volumeMount ->
      new KubernetesVolumeMount(name: volumeMount.name, readOnly: volumeMount.readOnly, mountPath: volumeMount.mountPath)
    }

    containerDescription.args = container?.args ?: []
    containerDescription.command = container?.command ?: []

    return containerDescription
  }

  static KubernetesVolumeSource fromVolume(V1Volume volume) {
    def res = new KubernetesVolumeSource(name: volume.name)

    if (volume.emptyDir) {
      res.type = KubernetesVolumeSourceType.EmptyDir
      def medium = volume.emptyDir.medium
      def mediumType

      if (medium == "Memory") {
        mediumType = KubernetesStorageMediumType.Memory
      } else {
        mediumType = KubernetesStorageMediumType.Default
      }

      res.emptyDir = new KubernetesEmptyDir(medium: mediumType)
    } else if (volume.hostPath) {
      res.type = KubernetesVolumeSourceType.HostPath
      res.hostPath = new KubernetesHostPath(path: volume.hostPath.path)
    } else if (volume.persistentVolumeClaim) {
      res.type = KubernetesVolumeSourceType.PersistentVolumeClaim
      res.persistentVolumeClaim = new KubernetesPersistentVolumeClaim(claimName: volume.persistentVolumeClaim.claimName,
        readOnly: volume.persistentVolumeClaim.readOnly)
    } else if (volume.secret) {
      res.type = KubernetesVolumeSourceType.Secret
      res.secret = new KubernetesSecretVolumeSource(secretName: volume.secret.secretName)
    } else if (volume.configMap) {
      res.type = KubernetesVolumeSourceType.ConfigMap
      def items = volume.configMap.items?.collect { V1KeyToPath item ->
       new KubernetesKeyToPath(key: item.key, path: item.path)
      }
      res.configMap = new KubernetesConfigMapVolumeSource(configMapName: volume.configMap.name, items: items)
    } else if (volume.awsElasticBlockStore) {
      res.type = KubernetesVolumeSourceType.AwsElasticBlockStore
      def ebs = volume.awsElasticBlockStore
      res.awsElasticBlockStore = new KubernetesAwsElasticBlockStoreVolumeSource(volumeId: ebs.volumeID,
        fsType: ebs.fsType,
        partition: ebs.partition)
    } else {
      res.type = KubernetesVolumeSourceType.Unsupported
    }

    return res
  }

  static KubernetesExecAction fromExecAction(V1ExecAction exec) {
    if (!exec) {
      return null
    }

    def kubernetesExecAction = new KubernetesExecAction()
    kubernetesExecAction.commands = exec.command
    return kubernetesExecAction
  }

  static KubernetesHandler fromHandler(V1Handler handler) {
    def kubernetesHandler = new KubernetesHandler()
    if (handler.exec) {
      kubernetesHandler.execAction = fromExecAction(handler.exec)
      kubernetesHandler.type = KubernetesHandlerType.EXEC
    }

    if (handler.tcpSocket) {
      kubernetesHandler.tcpSocketAction = fromTcpSocketAction(handler.tcpSocket)
      kubernetesHandler.type = KubernetesHandlerType.TCP
    }

    if (handler.httpGet) {
      kubernetesHandler.httpGetAction = fromHttpGetAction(handler.httpGet)
      kubernetesHandler.type = KubernetesHandlerType.HTTP
    }

    return kubernetesHandler
  }

  static KubernetesHttpGetAction fromHttpGetAction(V1HTTPGetAction httpGet) {
    if (!httpGet) {
      return null
    }

    def kubernetesHttpGetAction = new KubernetesHttpGetAction()
    kubernetesHttpGetAction.host = httpGet.host
    kubernetesHttpGetAction.path = httpGet.path
    kubernetesHttpGetAction.port = httpGet.port?.toInteger() ?: 0
    kubernetesHttpGetAction.uriScheme = httpGet.scheme
    kubernetesHttpGetAction.httpHeaders = httpGet.httpHeaders?.collect() {
      new KeyValuePair(name: it.name, value: it.value)
    }
    return kubernetesHttpGetAction
  }

  static KubernetesTcpSocketAction fromTcpSocketAction(V1TCPSocketAction tcpSocket) {
    if (!tcpSocket) {
      return null
    }

    def kubernetesTcpSocketAction = new KubernetesTcpSocketAction()
    kubernetesTcpSocketAction.port = tcpSocket.port ?: 0
    return kubernetesTcpSocketAction
  }

  static KubernetesProbe fromV1Probe(V1Probe probe) {
    if (!probe) {
      return null
    }

    def kubernetesProbe = new KubernetesProbe()
    kubernetesProbe.failureThreshold = probe.failureThreshold ?: 0
    kubernetesProbe.successThreshold = probe.successThreshold ?: 0
    kubernetesProbe.timeoutSeconds = probe.timeoutSeconds ?: 0
    kubernetesProbe.periodSeconds = probe.periodSeconds ?: 0
    kubernetesProbe.initialDelaySeconds = probe.initialDelaySeconds ?: 0
    kubernetesProbe.handler = new KubernetesHandler()

    if (probe.exec) {
      kubernetesProbe.handler.execAction = fromExecAction(probe.exec)
      kubernetesProbe.handler.type = KubernetesHandlerType.EXEC
    }

    if (probe.tcpSocket) {
      kubernetesProbe.handler.tcpSocketAction = fromTcpSocketAction(probe.tcpSocket)
      kubernetesProbe.handler.type = KubernetesHandlerType.TCP
    }

    if (probe.httpGet) {
      kubernetesProbe.handler.httpGetAction = fromHttpGetAction(probe.httpGet)
      kubernetesProbe.handler.type = KubernetesHandlerType.HTTP
    }

    return kubernetesProbe
  }

  /**
   * Let me know if this Api has to go in kubernetes-client/java
   * @param obj
   * @return
   */
  static String getYaml(Object obj){
    ObjectMapper m = new ObjectMapper(new YAMLFactory());
    return   m.writeValueAsString(obj).replaceAll("\\\\", "");
  }

}
