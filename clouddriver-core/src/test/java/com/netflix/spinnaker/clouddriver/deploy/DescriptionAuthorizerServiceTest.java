/*
 * Copyright 2023 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountSecurityPolicy;
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DescriptionAuthorizerServiceTest {

  private final NoopRegistry registry = new NoopRegistry();
  private final FiatPermissionEvaluator evaluator = mock(FiatPermissionEvaluator.class);
  private final UserSecretManager userSecretManager = mock(UserSecretManager.class);
  private SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps;
  private DescriptionAuthorizerService service;

  @BeforeEach
  public void setup() {
    opsSecurityConfigProps = new SecurityConfig.OperationsSecurityConfigurationProperties();
    service =
        new DescriptionAuthorizerService(
            registry,
            Optional.of(evaluator),
            opsSecurityConfigProps,
            new AccountDefinitionSecretManager(
                userSecretManager, new DefaultAccountSecurityPolicy(evaluator)));
    TestingAuthenticationToken auth = new TestingAuthenticationToken(null, null);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  public void shouldAuthorizePassedDescription() {
    TestDescription description =
        new TestDescription(
            "testAccount",
            Arrays.asList("testApplication", null),
            Arrays.asList("testResource1", "testResource2", null));

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    when(evaluator.hasPermission(any(Authentication.class), anyString(), anyString(), anyString()))
        .thenReturn(false);

    service.authorize(description, errors);

    assertEquals(errors.getAllErrors().size(), 4);
    verify(evaluator, times(3))
        .hasPermission(any(Authentication.class), anyString(), anyString(), anyString());
    verify(evaluator, times(1)).storeWholePermission();
  }

  private static Stream<Arguments> provideSkipAuthenticationForImageTaggingArgs() {
    return Stream.of(
        Arguments.of(List.of("testAccount"), 0),
        Arguments.of(List.of("anotherAccount"), 1),
        Arguments.of(List.of(), 1));
  }

  @ParameterizedTest
  @MethodSource("provideSkipAuthenticationForImageTaggingArgs")
  public void shouldSkipAuthenticationForImageTaggingDescription(
      List<String> allowUnauthenticatedImageTaggingInAccounts, int expectedNumberOfErrors) {
    TestImageTaggingDescription description = new TestImageTaggingDescription("testAccount");
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    opsSecurityConfigProps.setAllowUnauthenticatedImageTaggingInAccounts(
        allowUnauthenticatedImageTaggingInAccounts);

    service.authorize(description, errors);

    assertEquals(errors.getAllErrors().size(), expectedNumberOfErrors);
    verify(evaluator, never())
        .hasPermission(any(Authentication.class), anyString(), anyString(), anyString());
    verify(evaluator, never()).storeWholePermission();
  }

  @ParameterizedTest
  @CsvSource({"APPLICATION, 3, 3", "ACCOUNT, 0, 1"})
  public void shouldOnlyAuthzSpecifiedResourceType(
      ResourceType resourceType, int expectedNumberOfAuthChecks, int expectedNumberOfErrors) {
    TestDescription description =
        new TestDescription(
            "testAccount",
            Arrays.asList("testApplication", null),
            Arrays.asList("testResource1", "testResource2", null));

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    when(evaluator.hasPermission(any(Authentication.class), anyString(), anyString(), anyString()))
        .thenReturn(false);

    service.authorize(description, errors, List.of(resourceType));

    assertEquals(errors.getAllErrors().size(), expectedNumberOfErrors);
    verify(evaluator, times(expectedNumberOfAuthChecks))
        .hasPermission(any(Authentication.class), any(), any(), any());
  }

  @Getter
  public static class TestDescription
      implements AccountNameable, ApplicationNameable, ResourcesNameable {
    String account;
    Collection<String> applications;
    List<String> names;

    public TestDescription(String account, Collection<String> applications, List<String> names) {
      this.account = account;
      this.applications = applications;
      this.names = names;
    }
  }

  @Getter
  public static class TestImageTaggingDescription implements AccountNameable {
    String account;

    public TestImageTaggingDescription(String account) {
      this.account = account;
    }

    @Override
    public boolean requiresApplicationRestriction() {
      return false;
    }

    @Override
    public boolean requiresAuthorization(
        SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps) {
      return !opsSecurityConfigProps
          .getAllowUnauthenticatedImageTaggingInAccounts()
          .contains(account);
    }
  }
}
