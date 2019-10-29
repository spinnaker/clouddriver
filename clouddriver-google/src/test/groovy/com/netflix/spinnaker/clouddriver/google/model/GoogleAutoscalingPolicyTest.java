/*
 * Copyright 2019 Google, LLC
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
 */

package com.netflix.spinnaker.clouddriver.google.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.compute.model.AutoscalingPolicy;
import com.google.api.services.compute.model.AutoscalingPolicyCpuUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyCustomMetricUtilization;
import com.google.api.services.compute.model.AutoscalingPolicyLoadBalancingUtilization;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class GoogleAutoscalingPolicyTest {

  @Test
  void fromComputeModel_allFields() {
    AutoscalingPolicy input =
        new AutoscalingPolicy()
            .setCoolDownPeriodSec(123)
            .setCpuUtilization(new AutoscalingPolicyCpuUtilization().setUtilizationTarget(9.87))
            .setLoadBalancingUtilization(
                new AutoscalingPolicyLoadBalancingUtilization().setUtilizationTarget(6.54))
            .setMaxNumReplicas(99)
            .setMinNumReplicas(11)
            .setMode("ON")
            .setCustomMetricUtilizations(
                ImmutableList.of(
                    new AutoscalingPolicyCustomMetricUtilization()
                        .setMetric("myMetric")
                        .setUtilizationTarget(911.23)
                        .setUtilizationTargetType("GAUGE"),
                    new AutoscalingPolicyCustomMetricUtilization()));
    GoogleAutoscalingPolicy converted = GoogleAutoscalingPolicy.fromComputeModel(input);

    assertThat(converted.getCoolDownPeriodSec()).isEqualTo(input.getCoolDownPeriodSec());
    assertThat(converted.getCpuUtilization().getUtilizationTarget())
        .isEqualTo(input.getCpuUtilization().getUtilizationTarget());
    assertThat(converted.getLoadBalancingUtilization().getUtilizationTarget())
        .isEqualTo(input.getLoadBalancingUtilization().getUtilizationTarget());
    assertThat(converted.getMaxNumReplicas()).isEqualTo(input.getMaxNumReplicas());
    assertThat(converted.getMinNumReplicas()).isEqualTo(input.getMinNumReplicas());
    assertThat(converted.getMode().toString()).isEqualTo(input.getMode());

    assertThat(converted.getCustomMetricUtilizations())
        .hasSize(input.getCustomMetricUtilizations().size());
    for (int i = 0; i < converted.getCustomMetricUtilizations().size(); ++i) {
      CustomMetricUtilization convertedCustomMetric =
          converted.getCustomMetricUtilizations().get(0);
      AutoscalingPolicyCustomMetricUtilization inputCustomMetric =
          input.getCustomMetricUtilizations().get(0);
      assertThat(convertedCustomMetric.getMetric()).isEqualTo(inputCustomMetric.getMetric());
      assertThat(convertedCustomMetric.getUtilizationTarget())
          .isEqualTo(inputCustomMetric.getUtilizationTarget());
      assertThat(convertedCustomMetric.getUtilizationTargetType().toString())
          .isEqualTo(inputCustomMetric.getUtilizationTargetType());
    }
  }

  @Test
  void fromComputeModel_noFields() {
    AutoscalingPolicy input = new AutoscalingPolicy();
    GoogleAutoscalingPolicy converted = GoogleAutoscalingPolicy.fromComputeModel(input);

    assertThat(converted.getCoolDownPeriodSec()).isNull();
    assertThat(converted.getCpuUtilization()).isNull();
    assertThat(converted.getCustomMetricUtilizations()).isNull();
    assertThat(converted.getLoadBalancingUtilization()).isNull();
    assertThat(converted.getMaxNumReplicas()).isNull();
    assertThat(converted.getMinNumReplicas()).isNull();
    assertThat(converted.getMode()).isNull();
  }
}
