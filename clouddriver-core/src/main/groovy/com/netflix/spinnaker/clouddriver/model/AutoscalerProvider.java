/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import com.netflix.spinnaker.clouddriver.documentation.Empty;
import java.util.Set;

/**
 * An autoscaler provider is an interface for which {@link Autoscaler} objects may be retrieved.
 * This interface defines a common contract for which various providers may be queried about their
 * known autoscalers.
 */
public interface AutoscalerProvider<T extends Autoscaler> {
  /**
   * Returns all autoscalers related to an application.
   *
   * @param application the name of the application
   * @return a collection of autoscalers with all attributes populated and summaries {@link
   *     ServerGroupSummary} of the targeted server groups
   */
  @Empty
  Set<T> getAutoscalersByApplication(String application);
}
