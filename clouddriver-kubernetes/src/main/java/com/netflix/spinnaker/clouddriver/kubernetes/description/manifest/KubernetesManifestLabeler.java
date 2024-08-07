/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.google.common.base.Strings;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;

public class KubernetesManifestLabeler {
  private static final String SPINNAKER_LABEL = "spinnaker.io";
  private static final String MONIKER_LABEL_PREFIX = "moniker." + SPINNAKER_LABEL;
  private static final String SEQUENCE = MONIKER_LABEL_PREFIX + "/sequence";

  private static final String KUBERNETES_LABEL = "kubernetes.io";
  private static final String APP_LABEL_PREFIX = "app." + KUBERNETES_LABEL;
  private static final String APP_NAME = APP_LABEL_PREFIX + "/name";
  private static final String APP_VERSION = APP_LABEL_PREFIX + "/version";
  private static final String APP_COMPONENT = APP_LABEL_PREFIX + "/component";
  private static final String APP_PART_OF = APP_LABEL_PREFIX + "/part-of";
  private static final String APP_MANAGED_BY = APP_LABEL_PREFIX + "/managed-by";

  private static void storeLabelAndOverwrite(Map<String, String> labels, String key, String value) {
    if (value == null) {
      return;
    }

    labels.put(key, value);
  }

  private static void storeLabel(Map<String, String> labels, String key, String value) {
    if (value == null) {
      return;
    }

    if (labels.containsKey(key)) {
      return;
    }

    labels.put(key, value);
  }

  public static void labelManifest(
      String managedBySuffix,
      KubernetesManifest manifest,
      Moniker moniker,
      Boolean skipSpecTemplateLabels) {
    Map<String, String> labels = manifest.getLabels();
    storeLabels(managedBySuffix, labels, moniker);

    // Deployment fails for some Kubernetes resources (e.g. Karpenter NodePool) when
    // the app.kubernetes.io/* labels are applied to the manifest's
    // .spec.template.metadata.labels. If skipSpecTemplateLabels is
    // set to true in the manifest description, Spinnaker won't apply
    // the Kubernetes and Moniker labels
    // to the .spec.template.metadata.labels of the manifest.
    if (!skipSpecTemplateLabels) {
      manifest.getSpecTemplateLabels().ifPresent(l -> storeLabels(managedBySuffix, l, moniker));
    }
  }

  public static void storeLabels(
      String managedBySuffix, Map<String, String> labels, Moniker moniker) {
    if (moniker == null) {
      return;
    }

    String appManagedByValue = "spinnaker";

    if (!Strings.isNullOrEmpty(managedBySuffix)) {
      appManagedByValue = appManagedByValue + "-" + managedBySuffix;
    }

    // other properties aren't currently set by Spinnaker
    storeLabel(labels, APP_NAME, moniker.getApp());
    storeLabelAndOverwrite(labels, APP_MANAGED_BY, appManagedByValue);
    if (moniker.getSequence() != null) {
      storeLabelAndOverwrite(labels, SEQUENCE, moniker.getSequence() + "");
    }
  }

  public static KubernetesApplicationProperties getApplicationProperties(
      KubernetesManifest manifest) {
    Map<String, String> labels = manifest.getLabels();

    return new KubernetesApplicationProperties()
        .setName(labels.get(APP_NAME))
        .setVersion(labels.get(APP_VERSION))
        .setComponent(labels.get(APP_COMPONENT))
        .setPartOf(labels.get(APP_PART_OF))
        .setManagedBy(labels.get(APP_MANAGED_BY));
  }
}
