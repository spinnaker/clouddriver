/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class CreateApplication {
  private final String name;
  private final Map<String, ToOneRelationship> relationships;

  @Nullable private final Map<String, String> environmentVariables;

  @Nullable private final Lifecycle lifecycle;

  public CreateApplication(
      String name,
      Map<String, ToOneRelationship> relationships,
      @Nullable Map<String, String> environmentVariables,
      Lifecycle lifecycle) {
    this.name = name;
    this.relationships = relationships;
    this.environmentVariables = environmentVariables;
    this.lifecycle = lifecycle;
  }
}
