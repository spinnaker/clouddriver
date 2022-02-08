/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

@NonnullByDefault
public class InMemoryAccountDefinitionRepository implements AccountDefinitionRepository {
  private final Map<String, CredentialsDefinition> accounts = new ConcurrentHashMap<>();

  @Nullable
  @Override
  public CredentialsDefinition getByName(String name) {
    return accounts.get(name);
  }

  @Override
  public List<? extends CredentialsDefinition> listByType(
      String typeName, int limit, @Nullable String startingAccountName) {
    return accounts.values().stream()
        .filter(
            definition ->
                typeName.equals(AccountDefinitionMapper.getJsonTypeName(definition.getClass())))
        .sorted(Comparator.comparing(CredentialsDefinition::getName))
        .filter(
            definition ->
                startingAccountName == null
                    || startingAccountName.compareTo(definition.getName()) >= 0)
        .limit(limit)
        .collect(Collectors.toList());
  }

  @Override
  public List<? extends CredentialsDefinition> listByType(String typeName) {
    return accounts.values().stream()
        .filter(
            definition ->
                typeName.equals(AccountDefinitionMapper.getJsonTypeName(definition.getClass())))
        .sorted(Comparator.comparing(CredentialsDefinition::getName))
        .collect(Collectors.toList());
  }

  @Override
  public void create(CredentialsDefinition definition) {
    accounts.putIfAbsent(definition.getName(), definition);
  }

  @Override
  public void update(CredentialsDefinition definition) {
    accounts.put(definition.getName(), definition);
  }

  @Override
  public void delete(String name) {
    accounts.remove(name);
  }

  @Override
  public List<Revision> revisionHistory(String name) {
    return List.of();
  }
}
