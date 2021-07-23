/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.CreateLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;


public class CreateLambdaFunctionAtomicOperationConverterTest {

  private CreateLambdaFunctionAtomicOperationConverter converter = new CreateLambdaFunctionAtomicOperationConverter();
  private AccountCredentialsProvider provider = mock(AccountCredentialsProvider.class);

  @Test
  void shouldConvertInput() {
    converter.setObjectMapper(new ObjectMapper());
    converter.setAccountCredentialsProvider(provider);

    Map<String, Object> input = Map.of(
      "functionName", "function-1",
      "description", "somedescription",
      "credentials", "creds",
      "account", "creds",
      "region", "us-west-1",
      "appName", "app1"
    );

    when(provider.get())

    CreateLambdaFunctionDescription description = converter.convertDescription(input);

    assertThat(description.getAppName()).isEqualTo("app1");
    assertThat(description.getAccount()).isEqualTo("creds");

  }
}
