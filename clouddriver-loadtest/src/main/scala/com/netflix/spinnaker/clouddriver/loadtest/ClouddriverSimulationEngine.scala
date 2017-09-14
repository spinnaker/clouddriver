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

import io.gatling.app.Gatling
import io.gatling.core.config.GatlingPropertiesBuilder

object ClouddriverSimulationEngine extends App {
  val props = new GatlingPropertiesBuilder
  props.simulationClass(classOf[ClouddriverSimulation].getName)
  props.resultsDirectory("build/reports/gatling")
  props.binariesDirectory("build/classes/main")

  Gatling.fromMap(props.build)
  sys.exit()
}
