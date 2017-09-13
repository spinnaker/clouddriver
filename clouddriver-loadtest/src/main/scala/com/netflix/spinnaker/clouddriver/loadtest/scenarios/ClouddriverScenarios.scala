/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.loadtest.scenarios

import com.netflix.spinnaker.clouddriver.loadtest.actions.ClouddriverActions
import io.gatling.core.Predef._
import io.gatling.core.feeder.FeederBuilder
import io.gatling.core.structure.ScenarioBuilder

object ClouddriverScenarios {
  def fetchApplications(): ScenarioBuilder = {
    scenario("Fetch applications (unexpanded)")
      .exec(ClouddriverActions.fetchApplications)
  }

  def fetchApplicationsExpanded(): ScenarioBuilder = {
    scenario("Fetch applications (expanded)")
      .exec(ClouddriverActions.fetchApplicationsExpanded)
  }

  def fetchServerGroups(applicationPattern: String, applications: FeederBuilder[Any]) : ScenarioBuilder = {
    scenario(s"Fetch server groups $applicationPattern")
      .feed(applications)
      .exec(ClouddriverActions.fetchServerGroupExpanded)
  }
}
