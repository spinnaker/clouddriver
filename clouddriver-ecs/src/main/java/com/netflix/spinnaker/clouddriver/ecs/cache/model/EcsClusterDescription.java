/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.amazonaws.services.ecs.model.Attachment;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class EcsClusterDescription extends EcsCluster {

  int activeServicesCount;
  List<Attachment> attachments;
  String attachmentsStatus;
  List<String> capacityProviders;
  List<DefaultCapacityProviderStrategy> defaultCapacityProviderStrategy;
  List<Settings> settings;
  int pendingTasksCount;
  int registeredContainerInstancesCount;
  int runningTasksCount;
  List<Statistics> statistics;
  List<Tags> tags;
  String status;
  List<Failures> failures;

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class DefaultCapacityProviderStrategy {
    String capacityProvider;
    int base;
    int weight;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Settings {
    String name;
    String value;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Statistics {
    String name;
    String value;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Tags {
    String key;
    String value;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Failures {
    String arn;
    String detail;
    String reason;
  }
}
