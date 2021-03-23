/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy;

import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AutoScalingWorker;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

/**
 * Allows for custom tags to be set on resources created as a result of autoscaling activity
 * (requires usage of launch templates).
 */
public interface AmazonResourceTagger {
  @NotNull
  default Collection<Tag> volumeTags(
      @NotNull AutoScalingWorker.AsgConfiguration asgConfiguration,
      @NotNull String serverGroupName) {
    return Collections.emptyList();
  }

  @Data(staticConstructor = "of")
  class Tag {
    final String key;
    final String value;
  }
}
