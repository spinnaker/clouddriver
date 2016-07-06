/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers

import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration.DeployDefaults
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory

class DefaultMigrateServerGroupStrategy implements MigrateServerGroupStrategy {

  AmazonClientProvider amazonClientProvider

  RegionScopedProviderFactory regionScopedProviderFactory

  DeployDefaults deployDefaults

  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy

  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy

  BasicAmazonDeployHandler basicAmazonDeployHandler

  DefaultMigrateServerGroupStrategy(AmazonClientProvider amazonClientProvider,
                                    BasicAmazonDeployHandler basicAmazonDeployHandler,
                                    RegionScopedProviderFactory regionScopedProviderFactory,
                                    DeployDefaults deployDefaults,
                                    MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                                    MigrateLoadBalancerStrategy migrateLoadBalancerStrategy) {

    this.amazonClientProvider = amazonClientProvider
    this.basicAmazonDeployHandler = basicAmazonDeployHandler
    this.regionScopedProviderFactory = regionScopedProviderFactory
    this.deployDefaults = deployDefaults
    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy
    this.migrateLoadBalancerStrategy = migrateLoadBalancerStrategy
  }
}
