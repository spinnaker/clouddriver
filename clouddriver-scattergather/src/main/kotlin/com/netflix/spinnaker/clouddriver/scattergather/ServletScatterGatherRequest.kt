/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather

import java.time.Duration
import javax.servlet.http.HttpServletRequest

/**
 * @param targets Target name (shard, etc) to base URL mapping
 * @param original The original servlet request that is initiating the scatter/gather operation
 * @param timeout The amount of time that scattered requests are given to complete
 */
data class ServletScatterGatherRequest(
  val targets: Map<String, String>,
  val original: HttpServletRequest,
  val timeout: Duration = Duration.ofSeconds(60)
)
