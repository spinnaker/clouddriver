/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.config

import spock.lang.Specification

class DockerRegistryConfigurationPropertiesSpec extends Specification {
  void "DockerRegistryConfigurationProperties#ManagedAccount should be comparable"() {
    when:
    def account1 = createAccount('docker')
    def account2 = createAccount('docker')
    def account3 = createAccount('docker2')

    then:
    account1 == account2
    account1 != account3
  }

  def createAccount(name) {
    def account = new DockerRegistryConfigurationProperties.ManagedAccount()
    account.name = name
    account.environment = 'production'
    account.accountType = 'dockerRegistry'
    account.username = 'docker-user'
    account.password = 'test-password'
    account.address = 'hub.docker.com'
    account.cacheThreads = 5
    account.cacheIntervalSeconds = 6
    account.clientTimeoutMillis = 700
    account.repositories = ['repo-1', 'repo-2']
    account
  }
}
