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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.fiat.model.resources.Permissions;

import java.util.List;
import java.util.Objects;

/**
 * Provides an Amazon credential pack that uses Assume Role (http://docs.aws.amazon.com/IAM/latest/UserGuide/roles-assume-role.html) to provide API access to the account.
 * This class allows you to use your credentials, provided via the supplied {@link com.amazonaws.auth.AWSCredentialsProvider} to act-as the target account ID with the privileges desribed through the <b>assumeRole</b> role
 *
 *
 */
public class AssumeRoleAmazonCredentials extends AmazonCredentials {
    static final String DEFAULT_SESSION_NAME = "Spinnaker";

    static AWSCredentialsProvider createSTSCredentialsProvider(AWSCredentialsProvider credentialsProvider, String accountId, String assumeRole, String sessionName) {
        String assumeRoleValue = Objects.requireNonNull(assumeRole, "assumeRole");
        if (!assumeRoleValue.startsWith("arn:")) {
          assumeRoleValue = String.format("arn:aws:iam::%s:%s", Objects.requireNonNull(accountId, "accountId"), assumeRoleValue);
        }
        return credentialsProvider == null ? null : new NetflixSTSAssumeRoleSessionCredentialsProvider(
          credentialsProvider,
          assumeRoleValue,
          Objects.requireNonNull(sessionName, "sessionName"),
          accountId
        );
    }

    /**
     * The role to assume on the target account.
     */
    private final String assumeRole;
    private final String sessionName;

    public AssumeRoleAmazonCredentials(@JsonProperty("name") String name,
                                       @JsonProperty("environment") String environment,
                                       @JsonProperty("accountType") String accountType,
                                       @JsonProperty("accountId") String accountId,
                                       @JsonProperty("defaultKeyPair") String defaultKeyPair,
                                       @JsonProperty("regions") List<AWSRegion> regions,
                                       @JsonProperty("defaultSecurityGroups") List<String> defaultSecurityGroups,
                                       @JsonProperty("requiredGroupMembership") List<String> requiredGroupMembership,
                                       @JsonProperty("permissions") Permissions permissions,
                                       @JsonProperty("lifecycleHooks") List<LifecycleHook> lifecycleHooks,
                                       @JsonProperty("allowPrivateThirdPartyImages") boolean allowPrivateThirdPartyImages,
                                       @JsonProperty("assumeRole") String assumeRole,
                                       @JsonProperty("sessionName") String sessionName) {
        this(name,
             environment,
             accountType,
             accountId,
             defaultKeyPair,
             regions,
             defaultSecurityGroups,
             requiredGroupMembership,
             permissions,
             lifecycleHooks,
             allowPrivateThirdPartyImages,
             null,
             assumeRole,
             sessionName);
    }

    public AssumeRoleAmazonCredentials(AssumeRoleAmazonCredentials copy, AWSCredentialsProvider credentialsProvider) {
        this(copy.getName(),
             copy.getEnvironment(),
             copy.getAccountType(),
             copy.getAccountId(),
             copy.getDefaultKeyPair(),
             copy.getRegions(),
             copy.getDefaultSecurityGroups(),
             copy.getRequiredGroupMembership(),
             copy.getPermissions(),
             copy.getLifecycleHooks(),
             copy.getAllowPrivateThirdPartyImages(),
             credentialsProvider,
             copy.getAssumeRole(),
             copy.getSessionName());
    }

    AssumeRoleAmazonCredentials(String name,
                                String environment,
                                String accountType,
                                String accountId,
                                String defaultKeyPair,
                                List<AWSRegion> regions,
                                List<String> defaultSecurityGroups,
                                List<String> requiredGroupMembership,
                                Permissions permissions,
                                List<LifecycleHook> lifecycleHooks,
                                boolean allowPrivateThirdPartyImages,
                                AWSCredentialsProvider credentialsProvider,
                                String assumeRole,
                                String sessionName) {
        super(name,
              environment,
              accountType,
              accountId,
              defaultKeyPair,
              regions,
              defaultSecurityGroups,
              requiredGroupMembership,
              permissions,
              lifecycleHooks,
              allowPrivateThirdPartyImages,
              createSTSCredentialsProvider(credentialsProvider,
                                           accountId,
                                           assumeRole,
                                           sessionName == null ? DEFAULT_SESSION_NAME : sessionName));
        this.assumeRole = assumeRole;
        this.sessionName = sessionName == null ? DEFAULT_SESSION_NAME : sessionName;
    }

    public String getAssumeRole() {
        return assumeRole;
    }

    public String getSessionName() {
        return sessionName;
    }
}
