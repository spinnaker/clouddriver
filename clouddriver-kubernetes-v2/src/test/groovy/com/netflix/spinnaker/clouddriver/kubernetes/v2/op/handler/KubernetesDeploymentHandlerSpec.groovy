/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification

class KubernetesDeploymentHandlerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())
  def handler = new KubernetesDeploymentHandler()

  def IMAGE = "gcr.io/project/image"
  def CONFIG_MAP_VOLUME = "my-config-map"
  def SECRET_ENV = "my-secret-env"
  def CONFIG_MAP_ENV_KEY = "my-config-map-env"
  def ACCOUNT = "my-account"

  def BASIC_DEPLOYMENT = """
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: nginx-deployment
  namespace: nginx-namespace
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: $IMAGE
        ports:
        - containerPort: 80
        envFrom:
        - secretRef:
            name: $SECRET_ENV
        env:
        - name: KEY
          valueFrom:
            configMapKeyRef:
              name: $CONFIG_MAP_ENV_KEY
              key: value
      volumes:
      - configMap:
          name: $CONFIG_MAP_VOLUME
"""

  def DEPLOYMENT_NO_REPLICAS = """
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: nginx-deployment
  namespace: nginx-namespace
  labels:
    app: nginx
spec:
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: $IMAGE
        ports:
        - containerPort: 80
        envFrom:
        - secretRef:
            name: $SECRET_ENV
        env:
        - name: KEY
          valueFrom:
            configMapKeyRef:
              name: $CONFIG_MAP_ENV_KEY
              key: value
      volumes:
      - configMap:
          name: $CONFIG_MAP_VOLUME
"""

  def DEPLOYMENT_DIFFERENT = """
apiVersion: apps/v1beta2
kind: Deployment
metadata:
  name: nginx-deployment
  namespace: nginx-namespace
  labels:
    app: nginx
spec:
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx-2
        image: $IMAGE
        ports:
        - containerPort: 80
        envFrom:
        - secretRef:
            name: $SECRET_ENV
        env:
        - name: KEY
          valueFrom:
            configMapKeyRef:
              name: $CONFIG_MAP_ENV_KEY
              key: value
      volumes:
      - configMap:
          name: $CONFIG_MAP_VOLUME
"""

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  void "check that image is replaced by the artifact replacer"() {
    when:
    def target = "$IMAGE:version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.DockerImage.type)
        .name(IMAGE)
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].image == target
    result.boundArtifacts.size() == 1
    result.boundArtifacts.contains(artifact) == true
  }

  void "check that image isn't replaced by the artifact replacer"() {
    when:
    def target = "$IMAGE:version-bad"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.DockerImage.type)
        .name("not-$IMAGE")
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].image == IMAGE
    result.boundArtifacts.isEmpty() == true
  }

  void "check that image is found"() {
    when:
    def result = handler.listArtifacts(stringToManifest(BASIC_DEPLOYMENT))

    then:
    result.findAll { a -> a.getReference() == IMAGE && a.getType() == KubernetesArtifactType.DockerImage.type }.size() == 1
  }

  void "check that configmap volume is replaced by the artifact replacer without an account specified"() {
    when:
    def target = "$CONFIG_MAP_VOLUME-version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name(CONFIG_MAP_VOLUME)
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.volumes[0].configMap.name == target
  }

  void "check that configmap volume is replaced by the artifact replacer"() {
    when:
    def target = "$CONFIG_MAP_VOLUME-version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name(CONFIG_MAP_VOLUME)
        .reference(target)
        .metadata(["account": ACCOUNT])
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.volumes[0].configMap.name == target
  }

  void "check that configmap volume replaced by the artifact replacer"() {
    when:
    def target = "$CONFIG_MAP_VOLUME:version-bad"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name("not-$CONFIG_MAP_VOLUME")
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.volumes[0].configMap.name == CONFIG_MAP_VOLUME
  }

  void "check that configmap volume is not replaced by the artifact replacer in the wrong account"() {
    when:
    def target = "$CONFIG_MAP_VOLUME:version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name("$CONFIG_MAP_VOLUME")
        .reference(target)
        .metadata(["account": "not-$ACCOUNT".toString()])
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.volumes[0].configMap.name != target
  }

  void "check that configmap volume is found"() {
    when:
    def result = handler.listArtifacts(stringToManifest(BASIC_DEPLOYMENT))

    then:
    result.findAll { a -> a.getReference() == CONFIG_MAP_VOLUME && a.getType() == KubernetesArtifactType.ConfigMap.type}.size() == 1
  }


  void "check that only secret ref is replaced by the artifact replacer"() {
    when:
    def target = "$SECRET_ENV-version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.Secret.type)
        .name(SECRET_ENV)
        .reference(target)
        .metadata(["account": ACCOUNT])
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].envFrom[0].secretRef.name == target
  }

  void "check that secret ref is not replaced by the artifact replacer"() {
    when:
    def target = "$SECRET_ENV:version-bad"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.Secret.type)
        .name("not-$SECRET_ENV")
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].envFrom[0].secretRef.name == SECRET_ENV
  }

  void "check that secret ref is found"() {
    when:
    def result = handler.listArtifacts(stringToManifest(BASIC_DEPLOYMENT))

    then:
    result.findAll { a -> a.getReference() == SECRET_ENV && a.getType() == KubernetesArtifactType.Secret.type}.size() == 1
  }

  void "check that only configmap value ref is replaced by the artifact replacer"() {
    when:
    def target = "$CONFIG_MAP_ENV_KEY-version-1.2.3"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name(CONFIG_MAP_ENV_KEY)
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name == target
  }

  void "check that configmap value ref is not replaced by the artifact replacer"() {
    when:
    def target = "$CONFIG_MAP_ENV_KEY:version-bad"
    def artifact = Artifact.builder()
        .type(KubernetesArtifactType.ConfigMap.type)
        .name("not-$CONFIG_MAP_ENV_KEY")
        .reference(target)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(BASIC_DEPLOYMENT), [artifact], ACCOUNT)

    then:
    result.manifest.spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name == CONFIG_MAP_ENV_KEY
  }

  void "check that configmap value ref is found"() {
    when:
    def result = handler.listArtifacts(stringToManifest(BASIC_DEPLOYMENT))

    then:
    result.findAll { a -> a.getReference() == CONFIG_MAP_ENV_KEY && a.getType() == KubernetesArtifactType.ConfigMap.type}.size() == 1
  }

  void "check that an empty replica count does not change the deployment"() {
    setup:
    KubernetesV2Credentials deployer = Mock(KubernetesV2Credentials)
    KubernetesManifest manifestWithReplicas = stringToManifest(BASIC_DEPLOYMENT)
    KubernetesManifest manifestWithoutReplicas = stringToManifest(DEPLOYMENT_NO_REPLICAS)
    KubernetesManifest differentManifestWithoutReplicas = stringToManifest(DEPLOYMENT_DIFFERENT);
    ((Map)manifestWithoutReplicas.get("spec")).remove("replicas")

    when:
    handler.deploy(deployer, differentManifestWithoutReplicas)

    then:
    deployer.getLastApplied("Deployment", "nginx-namespace", "nginx-deployment") >> manifestWithReplicas;
    deployer.setLastApplied(manifestWithoutReplicas);
    deployer.deploy()
  }
}
