/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DcosAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DeployDcosAtomicOperationDescription extends DcosAtomicOperationDescription implements DeployDescription {
  String application
  String stack
  String freeFormDetails
  String namespace
  String restartPolicy
  Integer targetSize
  List<String> loadBalancers
  List<String> securityGroups
  List<DcosContainerDescription> containers
  List<DcosVolumeSource> volumeSources
  Capacity capacity
  DcosScalingPolicy scalingPolicy
  Map<String, String> replicaSetAnnotations
  Map<String, String> podAnnotations
  DcosSecurityContext securityContext
  DcosDeployment deployment

  @JsonIgnore
  Set<String> imagePullSecrets
}

@AutoClone
@Canonical
class Capacity {
  Integer min
  Integer max
  Integer desired
}

@AutoClone
@Canonical
class DcosContainerPort {
  String name
  Integer containerPort
  String protocol
  String hostIp
  Integer hostPort
}

@AutoClone
@Canonical
class DcosImageDescription {
  String repository
  String tag
  String registry
}

@AutoClone
@Canonical
class DcosContainerDescription {
  String name
  DcosImageDescription imageDescription
  DcosPullPolicy imagePullPolicy

  DcosResourceDescription requests
  DcosResourceDescription limits

  List<DcosContainerPort> ports

  DcosProbe livenessProbe
  DcosProbe readinessProbe

  DcosLifecycle lifecycle

  List<DcosVolumeMount> volumeMounts
  List<DcosEnvVar> envVars

  List<String> command
  List<String> args

  DcosSecurityContext securityContext
}

@AutoClone
@Canonical
class DcosDeployment {
  boolean enabled
  DcosStrategy deploymentStrategy
  int minReadySeconds
  Integer revisionHistoryLimit     // May be null
  boolean paused
  Integer rollbackRevision         // May be null
  Integer progressRollbackSeconds  // May be null
}

@AutoClone
@Canonical
class DcosStrategy {
  DcosStrategyType type
  DcosRollingUpdate rollingUpdate
}

@AutoClone
@Canonical
class DcosRollingUpdate {
  String maxUnavailable
  String maxSurge
}

enum DcosStrategyType {
  Recreate,
  RollingUpdate
}

@AutoClone
@Canonical
class DcosLifecycle {
  DcosHandler postStart
  DcosHandler preStop
}

@AutoClone
@Canonical
class DcosEnvVar {
  String name
  String value
  DcosEnvVarSource envSource
}

@AutoClone
@Canonical
class DcosScalingPolicy {
  DcosCpuUtilization cpuUtilization
}

@AutoClone
@Canonical
class DcosCpuUtilization {
  Integer target
}

enum DcosPullPolicy {
  @JsonProperty("IFNOTPRESENT")
  IfNotPresent,

  @JsonProperty("ALWAYS")
  Always,

  @JsonProperty("NEVER")
  Never,
}

@AutoClone
@Canonical
class DcosEnvVarSource {
  DcosSecretSource secretSource
  DcosConfigMapSource configMapSource
}

@AutoClone
@Canonical
class DcosSecretSource {
  String secretName
  String key
}

@AutoClone
@Canonical
class DcosConfigMapSource {
  String configMapName
  String key
}

@AutoClone
@Canonical
class DcosVolumeMount {
  String name
  Boolean readOnly
  String mountPath
}

enum DcosVolumeSourceType {
  @JsonProperty("HOSTPATH")
  HostPath,

  @JsonProperty("EMPTYDIR")
  EmptyDir,

  @JsonProperty("PERSISTENTVOLUMECLAIM")
  PersistentVolumeClaim,

  @JsonProperty("SECRET")
  Secret,

  @JsonProperty("CONFIGMAP")
  ConfigMap,

  @JsonProperty("UNSUPPORTED")
  Unsupported,
}

enum DcosStorageMediumType {
  @JsonProperty("DEFAULT")
  Default,

  @JsonProperty("MEMORY")
  Memory,
}

@AutoClone
@Canonical
class DcosVolumeSource {
  String name
  DcosVolumeSourceType type
  DcosHostPath hostPath
  DcosEmptyDir emptyDir
  DcosPersistentVolumeClaim persistentVolumeClaim
  DcosSecretVolumeSource secret
  DcosConfigMapVolumeSource configMap
}

@AutoClone
@Canonical
class DcosConfigMapVolumeSource {
  String configMapName
  List<DcosKeyToPath> items
  Integer defaultMode
}

@AutoClone
@Canonical
class DcosKeyToPath {
  String key
  String path
  Integer defaultMode
}

@AutoClone
@Canonical
class DcosSecretVolumeSource {
  String secretName
}

@AutoClone
@Canonical
class DcosHostPath {
  String path
}

@AutoClone
@Canonical
class DcosEmptyDir {
  DcosStorageMediumType medium
}

@AutoClone
@Canonical
class DcosPersistentVolumeClaim {
  String claimName
  Boolean readOnly
}

@AutoClone
@Canonical
class DcosProbe {
  DcosHandler handler
  int initialDelaySeconds
  int timeoutSeconds
  int periodSeconds
  int successThreshold
  int failureThreshold
}

enum DcosHandlerType {
  EXEC, TCP, HTTP
}

@AutoClone
@Canonical
class DcosHandler {
  DcosHandlerType type
  DcosExecAction execAction
  DcosHttpGetAction httpGetAction
  DcosTcpSocketAction tcpSocketAction
}

@AutoClone
@Canonical
class DcosExecAction {
  List<String> commands
}

@AutoClone
@Canonical
class DcosHttpGetAction {
  String path
  int port
  String host
  String uriScheme
  List<KeyValuePair> httpHeaders
}

@AutoClone
@Canonical
class DcosTcpSocketAction {
  int port
}

@AutoClone
@Canonical
class DcosResourceDescription {
  String memory
  String cpu
}

@AutoClone
@Canonical
class KeyValuePair {
  String name
  String value
}

@AutoClone
@Canonical
class DcosSecurityContext {
  DcosCapabilities capabilities
  Boolean privileged
  DcosSeLinuxOptions seLinuxOptions
  Long runAsUser
  Boolean runAsNonRoot
  Boolean readOnlyRootFilesystem
}

@AutoClone
@Canonical
class DcosCapabilities {
  List<String> add
  List<String> drop
}

@AutoClone
@Canonical
class DcosSeLinuxOptions {
  String user
  String role
  String type
  String level
}
