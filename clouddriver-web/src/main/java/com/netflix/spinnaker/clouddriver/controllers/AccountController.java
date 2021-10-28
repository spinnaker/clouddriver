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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController("/accounts")
@ConditionalOnProperty("account.storage.enabled")
public class AccountController {
  private final AccountDefinitionRepository repository;

  public AccountController(AccountDefinitionRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  @PostFilter("hasPermission(filterObject.name, 'ACCOUNT', 'READ')")
  public List<? extends CredentialsDefinition> listAccountsByType(
      @RequestParam String accountType,
      @RequestParam OptionalInt limit,
      @RequestParam Optional<String> startingAccountName) {
    return repository.listByType(accountType, limit.orElse(100), startingAccountName.orElse(null));
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  public CredentialsDefinition createAccount(@RequestBody CredentialsDefinition definition) {
    repository.create(definition);
    return definition;
  }

  @PutMapping
  @PreAuthorize("hasPermission(#definition.name, 'ACCOUNT', 'WRITE')")
  public CredentialsDefinition updateAccount(@RequestBody CredentialsDefinition definition) {
    repository.update(definition);
    return definition;
  }

  @DeleteMapping("/{accountName}")
  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'WRITE')")
  public void deleteAccount(@PathVariable String accountName) {
    repository.delete(accountName);
  }

  @GetMapping("/{accountName}/history")
  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'READ')")
  public List<AccountDefinitionRepository.Revision> getAccountHistory(
      @PathVariable String accountName) {
    return repository.revisionHistory(accountName);
  }
}
