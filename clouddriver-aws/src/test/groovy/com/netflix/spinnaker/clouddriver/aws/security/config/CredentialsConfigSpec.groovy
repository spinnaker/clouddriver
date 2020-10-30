/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.aws.security.config

import spock.lang.Specification

class CredentialsConfigSpec extends Specification {

  def 'accounts are equal'() {
    setup:
    CredentialsConfig.Account account1 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account1")
      setEnabled(true)
      setDefaultSecurityGroups(["sg1", "sg2"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region1")}},
        new CredentialsConfig.Region(){{setName("region2")}},
      ])
    }}
    CredentialsConfig.Account account2 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account1")
      setEnabled(true)
      setDefaultSecurityGroups(["sg2", "sg1"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region2")}},
        new CredentialsConfig.Region(){{setName("region1")}},
      ])
    }}
    expect:
    account2.equals(account1)
  }

  def 'accounts are not equal'() {
    setup:
    CredentialsConfig.Account account1 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account2")
      setEnabled(true)
      setDefaultSecurityGroups(["sg1", "sg2"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region1")}},
        new CredentialsConfig.Region(){{setName("region2")}},
      ])
    }}
    CredentialsConfig.Account account2 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account1")
      setEnabled(true)
      setDefaultSecurityGroups(["sg2", "sg1"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region2")}},
        new CredentialsConfig.Region(){{setName("region1")}},
      ])
    }}

    expect:
    !account2.equals(account1)
  }

  def 'accounts are not equal due to regions'() {
    setup:
    CredentialsConfig.Account account1 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account1")
      setEnabled(true)
      setDefaultSecurityGroups(["sg1", "sg2"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region1")}},
        new CredentialsConfig.Region(){{setName("region2")}},
        new CredentialsConfig.Region(){{setName("region3")}},
      ])
    }}
    CredentialsConfig.Account account2 = new CredentialsConfig.Account(){{
      setAccountId("account1")
      setAssumeRole("account1")
      setName("account1")
      setEnabled(true)
      setDefaultSecurityGroups(["sg2", "sg1"])
      setRegions([
        new CredentialsConfig.Region(){{setName("region2")}},
        new CredentialsConfig.Region(){{setName("region1")}},
      ])
    }}

    expect:
    !account2.equals(account1)
  }

  def 'accounts are not equal due to null regions'() {
    setup:
    CredentialsConfig.Account account1 = new CredentialsConfig.Account(){{
      setRegions([
        new CredentialsConfig.Region(){{setName("region1")}}
      ])
    }}
    CredentialsConfig.Account account2 = new CredentialsConfig.Account(){{
      setRegions(null)
    }}

    expect:
    !account2.equals(account1)
  }

  def 'accounts are not equal due to null SGs'() {
    setup:
    CredentialsConfig.Account account1 = new CredentialsConfig.Account(){{
      setDefaultSecurityGroups([
        "sg-1234"
      ])
    }}
    CredentialsConfig.Account account2 = new CredentialsConfig.Account(){{
      setDefaultSecurityGroups(null)
    }}

    expect:
    !account2.equals(account1)
  }
}
