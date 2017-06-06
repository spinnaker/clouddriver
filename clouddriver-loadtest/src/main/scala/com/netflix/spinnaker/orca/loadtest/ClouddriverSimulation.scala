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
package com.netflix.spinnaker.orca.loadtest

import java.io.File

import com.netflix.spinnaker.orca.loadtest.scenarios.ClouddriverScenarios
import io.gatling.core.Predef._
import io.gatling.core.feeder.RecordSeqFeederBuilder
import io.gatling.core.structure.PopulationBuilder
import io.gatling.http.Predef._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class ClouddriverSimulation extends Simulation {

  val config = new ClouddriverSimulationConfig(ClouddriverSimulationConfig.loadConfig())

  setUp {
    createScenarioList()
  }

  def createScenarioList(): List[PopulationBuilder] = {
    val scenarios: ListBuffer[PopulationBuilder] = new ListBuffer()

    if (config.fetchApplications.rampUsersTo > 0) {
      scenarios.append(
        ClouddriverScenarios.fetchApplications().inject(
          rampUsersPerSec(config.fetchApplications.rampUsersPerSec) to config.fetchApplications.rampUsersTo during config.rampUpPeriod.seconds,
          constantUsersPerSec(config.fetchApplications.rampUsersTo) during config.duration
        ).protocols(http.baseURL(config.serviceUrl))
      )

      scenarios.append(
        ClouddriverScenarios.fetchApplicationsExpanded().inject(
          rampUsersPerSec(config.fetchApplications.rampUsersPerSec) to config.fetchApplications.rampUsersTo during config.rampUpPeriod.seconds,
          constantUsersPerSec(config.fetchApplications.rampUsersTo) during config.duration
        ).protocols(http.baseURL(config.serviceUrl))
      )
    }

    if (config.fetchServerGroups.rampUsersTo > 0) {
      config.fetchServerGroups.applicationFiles.foreach(a => {
        val applicationFeeder: RecordSeqFeederBuilder[Any] = jsonFile(a).circular
        scenarios.append(
          ClouddriverScenarios.fetchServerGroups(new File(a).getName, applicationFeeder).inject(
            rampUsersPerSec(config.fetchServerGroups.rampUsersPerSec) to config.fetchServerGroups.rampUsersTo during config.rampUpPeriod.seconds,
            constantUsersPerSec(config.fetchServerGroups.rampUsersTo) during config.duration
          ).protocols(http.baseURL(config.serviceUrl))
        )
      })
    }

    scenarios.toList
  }
}
