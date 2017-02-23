/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.ProtocolEnum;

import java.util.List;

public class UpsertAmazonLoadBalancerV2Description extends UpsertAmazonLoadBalancerDescription {
  public List<Listener> listeners;
  public List<TargetGroup> targetGroups;

  public static class TargetGroup {
    public String name;
    public ProtocolEnum protocol;
    public Integer port;
    public Attributes attributes; // TODO: Support target group attributes

    public ProtocolEnum healthCheckProtocol;
    public String healthCheckPath;
    public String healthCheckPort;
    public Integer healthCheckInterval = 10;
    public Integer healthCheckTimeout = 5;
    public Integer unhealthyThreshold = 2;
    public Integer healthyThreshold = 10;
    public String healthCheckMatcher = "200-299"; // string of ranges or individual http status codes, separated by commas

    public Boolean compare(com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup awsTargetGroup) {
      return this.name.equals(awsTargetGroup.getTargetGroupName()) &&
        this.protocol.toString().equals(awsTargetGroup.getProtocol()) &&
        this.port.equals(awsTargetGroup.getPort()) &&
        this.healthCheckProtocol.toString().equals(awsTargetGroup.getHealthCheckProtocol()) &&
        this.healthCheckPath.equals(awsTargetGroup.getHealthCheckPath()) &&
        this.healthCheckPort.equals(awsTargetGroup.getHealthCheckPort()) &&
        this.healthCheckInterval.equals(awsTargetGroup.getHealthCheckIntervalSeconds()) &&
        this.healthCheckTimeout.equals(awsTargetGroup.getHealthCheckTimeoutSeconds()) &&
        this.healthyThreshold.equals(awsTargetGroup.getHealthyThresholdCount()) &&
        this.unhealthyThreshold.equals(awsTargetGroup.getUnhealthyThresholdCount()) &&
        this.healthCheckMatcher.equals(awsTargetGroup.getMatcher().getHttpCode());

    }
  }

  public static class Listener {
    public ProtocolEnum protocol;
    public Integer port;
    public String sslPolicy;
    public List<Action> defaultActions;

    public Boolean compare(com.amazonaws.services.elasticloadbalancingv2.model.Listener awsListener, List<com.amazonaws.services.elasticloadbalancingv2.model.Action> actions) {
      Boolean actionsSame = awsListener.getDefaultActions().containsAll(actions) &&
        actions.containsAll(awsListener.getDefaultActions());
      Boolean sslPolicySame = (this.sslPolicy == null && awsListener.getSslPolicy() == null) ||
        (this.sslPolicy != null && this.sslPolicy.equals(awsListener.getSslPolicy()));

      return (this.protocol != null && this.protocol.toString().equals(awsListener.getProtocol())) &&
        (this.port != null && this.port.equals(awsListener.getPort())) &&
        actionsSame &&
        sslPolicySame;
    }
  }

  public static class Action {
    public ActionTypeEnum type = ActionTypeEnum.Forward;
    public String targetGroupName;
  }

  public static class Attributes {
    public Integer deregistrationDelay = 300;
    public Boolean stickinessEnabled = false;
    public String stickinessType = "lb_cookie";
    public Integer stickinessDuration = 86400;
  }
}
