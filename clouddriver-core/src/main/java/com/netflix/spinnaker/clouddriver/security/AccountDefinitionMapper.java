/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Maps account definitions to and from strings. Only {@link CredentialsDefinition} classes
 * annotated with a {@link com.fasterxml.jackson.annotation.JsonTypeName} will be considered. {@link
 * UserSecretReference} URIs may be used for credentials values which will be replaced with an
 * appropriate string for the secret along with recording an associated account name for time of use
 * permission checks on the user secret.
 */
@NonnullByDefault
@RequiredArgsConstructor
public class AccountDefinitionMapper {

  private final ObjectMapper objectMapper;
  private final AccountDefinitionSecretManager secretManager;

  public String serialize(CredentialsDefinition definition) throws JsonProcessingException {
    return objectMapper.writeValueAsString(definition);
  }

  public CredentialsDefinition deserialize(String string) throws JsonProcessingException {
    Map<String, Object> map = objectMapper.readerForMapOf(Object.class).readValue(string);
    String accountName = (String) map.get("name");
    for (var entry : map.entrySet()) {
      UserSecretReference.tryParse(entry.getValue())
          .ifPresent(ref -> entry.setValue(secretManager.getUserSecretString(ref, accountName)));
    }
    return objectMapper.convertValue(map, CredentialsDefinition.class);
  }
}
