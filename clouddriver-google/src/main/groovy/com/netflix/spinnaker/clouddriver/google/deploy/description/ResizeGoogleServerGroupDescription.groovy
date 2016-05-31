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

package com.netflix.spinnaker.clouddriver.google.deploy.description

class ResizeGoogleServerGroupDescription extends AbstractGoogleCredentialsDescription {
  String serverGroupName
  Integer targetSize
  String region
  String accountName

  /**
   * targetSize takes precedence if it and capacity are both specified.
   */
  Capacity capacity

  @Deprecated
  String zone

  /**
   * Reuse Spinnaker's notion of capacity in an effort to make Orca more generic.
   */
  static class Capacity {
    Integer desired
  }
}
