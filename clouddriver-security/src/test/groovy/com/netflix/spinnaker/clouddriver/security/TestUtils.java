/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;

public class TestUtils {

  public static NetflixAmazonCredentials buildNetflixAmazonCredentials(String accountName) {
    return new NetflixAmazonCredentials(accountName,
                                        "some-env",
                                        "some-account-type",
                                        "account-id-123",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null);
  }

  public static GoogleNamedAccountCredentials buildGoogleNamedAccountCredentials(String accountName) {
    return new GoogleNamedAccountCredentials.Builder().applicationName("app").name(accountName).credentials(new FakeGoogleCredentials()).build();
  }
}
