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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.clouddriver.config.AccountDefinitionConfiguration;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(classes = AccountDefinitionConfiguration.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class AccountDefinitionSecretManagerTest {

  @MockBean UserSecretManager userSecretManager;

  @MockBean AccountSecurityPolicy policy;

  @Autowired AccountDefinitionSecretManager secretManager;

  @Test
  void canAccessUserSecret() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group", "group2"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    var username = "user";
    var accountName = "account";
    given(policy.getRoles(username)).willReturn(Set.of("group"));
    given(policy.canUseAccount(username, accountName)).willReturn(true);

    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(secretManager.getUserSecretString(ref, accountName)).isEqualTo("bar");
    assertThat(secretManager.canAccessAccountWithSecrets(username, accountName)).isTrue();
  }

  @Test
  void adminHasAccess() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group", "group2"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(true);

    var ref = UserSecretReference.parse("secret://test?k=foo");
    var accountName = "cube";
    assertThat(secretManager.getUserSecretString(ref, accountName)).isEqualTo("bar");
    assertThat(secretManager.canAccessAccountWithSecrets("sphere", accountName)).isTrue();
  }

  @Test
  void cannotAccessUserSecret() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group0", "group1"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    given(policy.getRoles(any())).willReturn(Set.of("group2", "group3"));

    var accountName = "cube";
    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(secretManager.getUserSecretString(ref, accountName)).isEqualTo("bar");
    assertThat(secretManager.canAccessAccountWithSecrets("sphere", accountName)).isFalse();
  }

  @Test
  void canAccessSecretButNotAccount() {
    var userSecret = mock(UserSecret.class);
    given(userSecret.getRoles()).willReturn(List.of("group0", "group1"));
    given(userSecret.getSecretString(eq("foo"))).willReturn("bar");
    given(userSecretManager.getUserSecret(any())).willReturn(userSecret);
    given(policy.isAdmin(any())).willReturn(false);
    given(policy.getRoles(any())).willReturn(Set.of("group0", "group1"));
    given(policy.canUseAccount(any(), any())).willReturn(false);

    var accountName = "cube";
    var ref = UserSecretReference.parse("secret://test?k=foo");
    assertThat(secretManager.getUserSecretString(ref, accountName)).isEqualTo("bar");
    assertThat(secretManager.canAccessAccountWithSecrets("sphere", accountName)).isFalse();
  }
}
