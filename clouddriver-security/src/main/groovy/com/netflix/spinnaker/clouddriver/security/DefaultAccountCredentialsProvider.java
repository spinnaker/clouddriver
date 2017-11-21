/*
 * Copyright 2015 Netflix, Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class DefaultAccountCredentialsProvider implements AccountCredentialsProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAccountCredentialsProvider.class);

    private final AccountCredentialsRepository repository;

    public DefaultAccountCredentialsProvider() {
        this.repository = new MapBackedAccountCredentialsRepository();
    }

    public DefaultAccountCredentialsProvider(AccountCredentialsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Set<? extends AccountCredentials> getAll(boolean includeDisabledAccounts) {
        return repository.getAll().stream()
          .filter(accountCredentials -> accountCredentials.isEnabled() || includeDisabledAccounts)
          .collect(Collectors.toSet());
    }

    @Override
    public AccountCredentials getCredentials(String name) {
        AccountCredentials credentials = repository.getOne(name);
        if (credentials != null && !credentials.isEnabled()) {
            LOG.warn("The account '{}' ({}) is disabled because it cannot be accessed.", credentials.getName(), credentials.getAccountId());
        }
        return credentials;
    }
}
