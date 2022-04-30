/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestTraffic
import com.netflix.spinnaker.moniker.Moniker
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesManifestAnnotatorSpec extends Specification {
  def clusterKey = "moniker.spinnaker.io/cluster"
  def applicationKey = "moniker.spinnaker.io/application"

  private KubernetesManifest freshManifest() {
    def result = new KubernetesManifest()
    result.put("kind", "replicaSet")
    result.put("apiVersion", KubernetesApiVersion.V1.toString())
    result.put("metadata", ["annotations": [:]])
    return result
  }

  @Unroll
  void "manifests are annotated and deannotated symmetrically"() {
    expect:
    def manifest = freshManifest()
    def moniker = Moniker.builder()
      .cluster(cluster)
      .app(application)
      .build()

    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)
    moniker == KubernetesManifestAnnotater.getMoniker(manifest)

    where:
    loadBalancers  | securityGroups   | cluster | application
    []             | []               | ""      | ""
    []             | []               | "  "    | ""
    null           | null             | null    | null
    []             | null             | ""      | null
    ["lb"]         | ["sg"]           | ""      | null
    ["lb1", "lb2"] | ["sg"]           | "x"     | "my app"
    ["lb1", "lb2"] | null             | null    | null
    null           | ["x1, x2", "x3"] | null    | null
    ["1"]          | ["1"]            | "1"     | "1"
  }

  @Unroll
  void "manifests are annotated with the expected prefix"() {
    expect:
    def manifest = freshManifest()
    def moniker = Moniker.builder()
      .cluster(cluster)
      .app(application)
      .build()

    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)
    manifest.getAnnotations().get(clusterKey) == cluster
    manifest.getAnnotations().get(applicationKey) == application

    where:
    cluster | application
    ""      | ""
    "c"     | "a"
    ""      | "a"

  }

  void "setTraffic correctly sets traffic on a manifest without traffic defined"() {
    given:
    def manifest = freshManifest()
    def traffic = new KubernetesManifestTraffic(["service my-service"])

    when:
    KubernetesManifestAnnotater.setTraffic(manifest, traffic)

    then:
    KubernetesManifestAnnotater.getTraffic(manifest) == traffic
  }

  void "setTraffic is a no-op if the new traffic is equal to the existing traffic"() {
    given:
    def manifest
    def traffic = new KubernetesManifestTraffic(loadBalancers)

    when:
    manifest = freshManifest()
    KubernetesManifestAnnotater.setTraffic(manifest, traffic)
    KubernetesManifestAnnotater.setTraffic(manifest, traffic)

    then:
    KubernetesManifestAnnotater.getTraffic(manifest) == traffic

    where:
    loadBalancers << [
      [],
      ["service my-service"],
      ["service my-service", "service my-other-service"]
    ]
  }

  void "setTraffic fails if the new traffic is not equal to the existing traffic"() {
    given:
    def manifest
    def existingTraffic = new KubernetesManifestTraffic(existingLoadBalancers)
    def newTraffic = new KubernetesManifestTraffic(newLoadBalancers)

    when:
    manifest = freshManifest()
    KubernetesManifestAnnotater.setTraffic(manifest, existingTraffic)
    KubernetesManifestAnnotater.setTraffic(manifest, newTraffic)

    then:
    thrown(Exception)

    where:
    existingLoadBalancers                              | newLoadBalancers
    []                                                 | ["service my-service"]
    ["service my-service"]                             | []
    ["service my-service"]                             | ["service my-other-service"]
    ["service my-service"]                             | ["service my-service", "service my-other-service"]
    ["service my-service", "service my-other-service"] | ["service my-other-service", "service my-service"]
  }
}
