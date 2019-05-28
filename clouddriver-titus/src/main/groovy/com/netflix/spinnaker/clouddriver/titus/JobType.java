/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.titus;

import javax.annotation.Nonnull;

/** The supported Job types within Titus. */
public enum JobType {
  BATCH("batch"),
  SERVICE("service");

  private final String value;

  JobType(String value) {
    this.value = value;
  }

  @Nonnull
  public String value() {
    return value;
  }
}
