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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.netflix.spinnaker.clouddriver.config.AccountDefinitionConfiguration;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import io.spinnaker.test.security.TestAccount;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
    classes = {AccountDefinitionServiceTest.Config.class, AccountDefinitionConfiguration.class})
@TestPropertySource(
    properties = {
      "account.storage.additionalScanPackages = io.spinnaker.test.security",
      "account.storage.enabled = true"
    })
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@ComponentScan("com.netflix.spinnaker.kork.secrets")
class AccountDefinitionServiceTest {

  @MockBean FiatPermissionEvaluator permissionEvaluator;

  @Autowired AccountDefinitionService service;

  @Autowired AccountDefinitionRepository repository;

  @BeforeEach
  void setUp() {
    given(
            permissionEvaluator.hasPermission(
                any(Authentication.class), any(Serializable.class), eq("ACCOUNT"), eq("WRITE")))
        .willReturn(true);
    var userPermissions = new UserPermission();
    userPermissions.addResource(new Role("dev"));
    given(permissionEvaluator.getPermission(eq("diane"))).willReturn(userPermissions.getView());
    var userDetails =
        User.withUsername("diane").password("hunter2").authorities("authenticated").build();
    var user =
        new TestingAuthenticationToken(
            userDetails, userDetails.getPassword(), new ArrayList<>(userDetails.getAuthorities()));
    SecurityContextHolder.getContext().setAuthentication(user);
  }

  @Test
  void smokeTest() {
    var account = new TestAccount();
    account.setData("name", "roger");
    account.setData("password", "vector");
    account.getPermissions().add(Authorization.WRITE, List.of("sre", "dev"));
    account.getPermissions().add(Authorization.READ, List.of("sre", "dev"));
    service.createAccount(account);
    List<? extends CredentialsDefinition> results =
        service.listAccountDefinitionsByType("test", 1, null);
    assertThat(results).hasSize(1);
    CredentialsDefinition result = results.get(0);
    assertThat(result).isNotNull().isInstanceOf(TestAccount.class);
    assertThat(((TestAccount) result).getData().get("password")).isEqualTo("vector");
  }

  @Configuration
  static class Config {
    @Bean
    AccountDefinitionRepository accountDefinitionRepository() {
      return new InMemoryAccountDefinitionRepository();
    }

    @Bean
    AccountCredentialsProvider accountCredentialsProvider() {
      return new DefaultAccountCredentialsProvider();
    }
  }
}
