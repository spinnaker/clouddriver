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
package com.netflix.spinnaker.clouddriver.loadtest

import java.io.File
import scala.collection.JavaConverters._

import com.typesafe.config.{Config, ConfigFactory}

object ClouddriverSimulationConfig {

  def loadConfig(): Config = {
    val configFilePath = sys.props.get("simulation.config")

    if (configFilePath.isDefined) {
      val file = new File(configFilePath.get)
      ConfigFactory.parseFile(file)
    } else {
      ConfigFactory.parseResources("clouddriver-simulation.json")
    }
  }
}

class ClouddriverSimulationConfig(config: Config) {

  val serviceUrl = config.getString("service.clouddriver.serviceUrl")

  val fetchApplications = new {
    val constantUsersPerSec = config.getInt("service.clouddriver.fetchApplications.constantUsersPerSec")
    val constantUsersDurationSec = config.getInt("service.clouddriver.fetchApplications.constantUsersDurationSec")
  }

  val fetchServerGroups = new {
    val constantUsersPerSec = config.getInt("service.clouddriver.fetchServerGroups.constantUsersPerSec")
    val constantUsersDurationSec = config.getInt("service.clouddriver.fetchServerGroups.constantUsersDurationSec")
    val applicationFiles : Set[String] = config.getStringList("service.clouddriver.fetchServerGroups.applicationFiles").asScala.toSet
  }
}
