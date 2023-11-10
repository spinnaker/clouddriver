/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.google.health


import com.google.api.client.testing.http.MockLowLevelHttpRequest
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.compute.model.Project
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.compute.FakeGoogleComputeRequest
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.provider.agent.StubComputeFactory
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import com.netflix.spinnaker.credentials.CredentialsTypeBaseConfiguration
import org.springframework.boot.actuate.health.Status
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

class GoogleHealthIndicatorSpec extends Specification {

  private static final String ACCOUNT_NAME = "partypups"
  private static final String PROJECT = "myproject"
  private static final String REGION = "myregion"
  private static final String ZONE = REGION + "-myzone"

  private static final Registry REGISTRY = new NoopRegistry()
  GoogleConfigurationProperties googleConfigurationProperties

  void setup() {
    googleConfigurationProperties = new GoogleConfigurationProperties()
  }

  @Unroll
  def "health succeeds when google is reachable"() {
    setup:
    googleConfigurationProperties.health.setVerifyAccountHealth(true)

    def applicationContext = Mock(ApplicationContext)
    def project = new Project()
    project.setName(PROJECT)

    def compute = new StubComputeFactory()
      .setProjects(project)
      .create()

    def googleNamedAccountCredentials =
      new GoogleNamedAccountCredentials.Builder()
        .project(PROJECT)
        .name(ACCOUNT_NAME)
        .compute(compute)
        .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of(ZONE)))
        .build()

    def credentials = [googleNamedAccountCredentials]
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> credentials
    }

    def credentialsTypeBaseConfiguration = new CredentialsTypeBaseConfiguration(applicationContext, null)
    credentialsTypeBaseConfiguration.credentialsRepository = credentialsRepository

    def indicator = new GoogleHealthIndicator(
      REGISTRY,
      credentialsTypeBaseConfiguration,
      googleConfigurationProperties)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  @Unroll
  def "health succeeds when google check is disabled"() {
    setup:
    googleConfigurationProperties.health.setVerifyAccountHealth(false)

    def applicationContext = Mock(ApplicationContext)

    def credentialsTypeBaseConfiguration = new CredentialsTypeBaseConfiguration(applicationContext, null)

    def indicator = new GoogleHealthIndicator(
      REGISTRY,
      credentialsTypeBaseConfiguration,
      googleConfigurationProperties)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    health.status == Status.UP
    health.details.isEmpty()
  }

  private MockLowLevelHttpResponse project(MockLowLevelHttpRequest request) {
    return FakeGoogleComputeRequest.createWithException(new IOException("Read timed out")) as MockLowLevelHttpResponse
  }

  @Unroll
  def "health throws exception when google appears unreachable"() {
    setup:
    googleConfigurationProperties.health.setVerifyAccountHealth(true)

    def applicationContext = Mock(ApplicationContext)
    def project = new Project()
    project.setName(PROJECT)

    def compute = new StubComputeFactory()
      .setProjects(project)
      .setProjectException(new IOException("Read timed out"))
      .create()

    def googleNamedAccountCredentials =
      new GoogleNamedAccountCredentials.Builder()
        .project(PROJECT)
        .name(ACCOUNT_NAME)
        .compute(compute)
        .regionToZonesMap(ImmutableMap.of(REGION, ImmutableList.of(ZONE)))
        .build()

    def credentials = [googleNamedAccountCredentials]
    def credentialsRepository = Stub(CredentialsRepository) {
      getAll() >> credentials
    }

    def credentialsTypeBaseConfiguration = new CredentialsTypeBaseConfiguration(applicationContext, null)
    credentialsTypeBaseConfiguration.credentialsRepository = credentialsRepository

    def indicator = new GoogleHealthIndicator(
      REGISTRY,
      credentialsTypeBaseConfiguration,
      googleConfigurationProperties)

    when:
    indicator.checkHealth()
    def health = indicator.health()

    then:
    thrown(GoogleHealthIndicator.GoogleIOException)

    health == null
  }
}
