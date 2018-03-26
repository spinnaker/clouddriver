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

package com.netflix.spinnaker.clouddriver.titus.client;

import java.util.Collections;
import java.util.List;

public class TitusRegion {
    private final String name;
    private final String account;
    private final String endpoint;
    private final Boolean autoscalingEnabled;
    private final List<TitusFaultDomain> faultDomains;
    private final String apiVersion;

    private <T> T notNull(T val, String name) {
        if (val == null) {
            throw new NullPointerException(name);
        }
        return val;
    }

    public TitusRegion(String name,
                       String account,
                       String endpoint,
                       Boolean autoscalingEnabled,
                       List<TitusFaultDomain> faultDomains,
                       String apiVersion) {
        this.name = notNull(name, "name");
        this.account = notNull(account, "account");
        this.endpoint = EndpointValidator.validateEndpoint(endpoint);
        this.autoscalingEnabled = autoscalingEnabled;
        this.faultDomains = faultDomains == null ? Collections.emptyList() : Collections.unmodifiableList(faultDomains);
        this.apiVersion = apiVersion;
    }

    public TitusRegion(String name, String account, String endpoint, Boolean autoscalingEnabled, String apiVersion) {
        this(name, account, endpoint, autoscalingEnabled, Collections.emptyList(), apiVersion);
    }

    public String getAccount() {
        return account;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiVersion() {
    return apiVersion;
  }

    public Boolean isAutoscalingEnabled() {
      return autoscalingEnabled;
    }

  public List<TitusFaultDomain> getFaultDomains() {
        return faultDomains;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TitusRegion that = (TitusRegion) o;

        if (!name.equals(that.name)) return false;
        if (!account.equals(that.account)) return false;
        if (!endpoint.equals(that.endpoint)) return false;
        return faultDomains.equals(that.faultDomains);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + account.hashCode();
        result = 31 * result + endpoint.hashCode();
        result = 31 * result + faultDomains.hashCode();
        return result;
    }
}
