/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.listeners;


import com.netflix.spinnaker.clouddriver.security.CredentialsInitializerSynchronizable;
import com.netflix.spinnaker.clouddriver.security.ProviderSynchronizable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ConfigurationRefreshListener implements ApplicationListener<EnvironmentChangeEvent> {

  @Autowired
  List<CredentialsInitializerSynchronizable> credentialsSynchronizers;

  @Autowired
  List<ProviderSynchronizable> providerSynchronizers;

  @Override
  public void onApplicationEvent(EnvironmentChangeEvent event) {
    credentialsSynchronizers.forEach(CredentialsInitializerSynchronizable::synchronize);
    providerSynchronizers.forEach(ProviderSynchronizable::synchronize);
  }
}
