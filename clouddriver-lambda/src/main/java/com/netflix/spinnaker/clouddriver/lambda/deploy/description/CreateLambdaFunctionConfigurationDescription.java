/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.description;

import com.amazonaws.services.lambda.model.DeadLetterConfig;
import com.amazonaws.services.lambda.model.TracingConfig;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateLambdaFunctionConfigurationDescription
    extends AbstractLambdaFunctionDescription {
  String functionName;
  String description;
  String handler;
  Integer memory;
  String role;
  String runtime;
  Integer timeout;
  List<String> subnetIds;
  List<String> securityGroupIds;
  Map<String, String> envVariables;
  Map<String, String> tags;
  DeadLetterConfig deadLetterConfig;
  String encryptionKMSKeyArn;
  TracingConfig tracingConfig;
  String targetGroups;
  String runTime;
}
