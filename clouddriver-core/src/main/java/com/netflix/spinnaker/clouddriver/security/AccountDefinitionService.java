/*
 * Copyright 2022 Apple Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Service wrapper for an {@link AccountDefinitionRepository} which enforces permissions and other
 * validations.
 */
@Alpha
@NonnullByDefault
@RequiredArgsConstructor
public class AccountDefinitionService {
  private final AccountDefinitionRepository repository;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final FiatPermissionEvaluator permissionEvaluator;
  private final ObjectMapper objectMapper;

  /**
   * Lists accounts by type that the current user has {@link Authorization#WRITE} access. Users who
   * only have {@link Authorization#READ} access can only view related items like load balancers,
   * clusters, security groups, etc., that use the account, but they may not directly use or view
   * account definitions and credentials.
   *
   * @see AccountDefinitionRepository#listByType(String, int, String)
   */
  @PostFilter("hasPermission(filterObject.name, 'ACCOUNT', 'WRITE')")
  public List<? extends CredentialsDefinition> listAccountDefinitionsByType(
      String accountType, int limit, @Nullable String startingAccountName) {
    return repository.listByType(accountType, limit, startingAccountName);
  }

  @PreAuthorize("isAuthenticated()")
  public CredentialsDefinition createAccount(CredentialsDefinition definition) {
    String name = definition.getName();
    if (accountCredentialsProvider.getCredentials(name) != null) {
      throw new InvalidRequestException(
          String.format("Cannot create duplicate account (name: %s)", name));
    }
    validateAccountWritePermissions(definition, AccountAction.CREATE);
    repository.create(definition);
    return definition;
  }

  @PreAuthorize("hasPermission(#definition.name, 'ACCOUNT', 'WRITE')")
  public CredentialsDefinition updateAccount(CredentialsDefinition definition) {
    if (accountCredentialsProvider.getCredentials(definition.getName()) == null) {
      throw new InvalidRequestException(
          String.format(
              "Cannot update an account which does not exist (name: %s)", definition.getName()));
    }
    validateAccountWritePermissions(definition, AccountAction.UPDATE);
    repository.update(definition);
    return definition;
  }

  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'WRITE')")
  public void deleteAccount(String accountName) {
    if (accountCredentialsProvider.getCredentials(accountName) == null) {
      throw new InvalidRequestException(
          String.format("Cannot delete an account which does not exist (name: %s)", accountName));
    }
    repository.delete(accountName);
  }

  /**
   * Deletes an account by name if the current user has {@link Authorization#WRITE} access to the
   * given account.
   */
  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'WRITE')")
  public List<AccountDefinitionRepository.Revision> getAccountHistory(String accountName) {
    return repository.revisionHistory(accountName);
  }

  @SuppressWarnings("unchecked")
  private void validateAccountWritePermissions(
      CredentialsDefinition definition, AccountAction action) {
    var credentials =
        objectMapper.convertValue(definition, new TypeReference<Map<String, Object>>() {});
    var permission =
        permissionEvaluator.getPermission(AuthenticatedRequest.getSpinnakerUser().orElseThrow());
    if (permission.isAdmin()) {
      return;
    }
    var userRoles =
        permission.getRoles().stream().map(Role.View::getName).collect(Collectors.toSet());
    var permissions = (Map<String, List<String>>) credentials.getOrDefault("permissions", Map.of());
    var writeRoles = Set.copyOf(permissions.getOrDefault("WRITE", List.of()));
    if (Sets.intersection(userRoles, writeRoles).isEmpty()) {
      throw new InvalidRequestException(
          String.format(
              "Cannot %s account without specifying WRITE permissions for current user (name: %s)",
              action.name().toLowerCase(Locale.ROOT), definition.getName()));
    }
  }

  private enum AccountAction {
    CREATE,
    UPDATE
  }
}
