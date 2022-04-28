/*
 * Copyright 2022 JPMorgan Chase & Co
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.cluster

import com.netflix.spinnaker.cats.agent.Agent
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver.Companion.DEFAULT_ACCOUNT_REGEX
import com.netflix.spinnaker.cats.sql.cluster.SqlCachingPodsObserver.Companion.POD_ID
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.sql.ResultSet

class SqlCachingPodsObserverTest: JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should ignore agents for account names this replica does not support") {
      whenever(dynamicConfigService.getConfig(eq(Regex::class.java), eq("cache-sharding.account-name-regex"),
        any())).thenReturn(Regex("kubernetes-internal.*"))

      val sqlCachingPodsObserver = SqlCachingPodsObserver(
        jooq,
        nodeIdentity,
        null,
        dynamicConfigService
      )

      val runnableAgent: Agent = mock()
      whenever(runnableAgent.providerName).thenReturn("kubernetes")
      whenever(runnableAgent.agentType).thenReturn("kubernetes-internal-acc-1/KubernetesCoreCachingAgent[1/1]")
      assertTrue(sqlCachingPodsObserver.filter(runnableAgent))

      val ignoredAgent: Agent = mock()
      whenever(ignoredAgent.providerName).thenReturn("kubernetes")
      whenever(ignoredAgent.agentType).thenReturn("kubernetes-external-acc-1/KubernetesCoreCachingAgent[1/1]")
      assertFalse(sqlCachingPodsObserver.filter(ignoredAgent))
    }

    test("should accept all agents with no regex supplied") {
      whenever(dynamicConfigService.getConfig(eq(Regex::class.java), eq("cache-sharding.account-name-regex"),
        any())).thenReturn(DEFAULT_ACCOUNT_REGEX)

      val sqlCachingPodsObserver = SqlCachingPodsObserver(
        jooq,
        nodeIdentity,
        null,
        dynamicConfigService
      )

      val agentOne: Agent = mock()
      whenever(agentOne.providerName).thenReturn("kubernetes")
      whenever(agentOne.agentType).thenReturn("kubernetes-internal-acc-1/KubernetesCoreCachingAgent[1/1]")
      assertTrue(sqlCachingPodsObserver.filter(agentOne))

      val agentTwo: Agent = mock()
      whenever(agentTwo.providerName).thenReturn("kubernetes")
      whenever(agentTwo.agentType).thenReturn("kubernetes-external-acc-1/KubernetesCoreCachingAgent[1/1]")
      assertTrue(sqlCachingPodsObserver.filter(agentTwo))
    }
  }

  private inner class Fixture {
    val jooq: DSLContext = mock()
    val nodeIdentity: NodeIdentity = mock()
    val dynamicConfigService: DynamicConfigService = mock()

    init {
      whenever(nodeIdentity.nodeIdentity).thenReturn("node1")
      whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("cache-sharding.replica-ttl-seconds"),
          any())).thenReturn(60)
      whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("cache-sharding.heartbeat-interval-seconds"),
          any())).thenReturn(30)

      // joins (aka SQL FROM)
      val sjs: SelectJoinStep<Record> = mock() // used for getting record for this pod
      val sjsExisting: SelectJoinStep<Record> = mock() // used for getting existing replicas
      val sjsCurrent: SelectJoinStep<Record> = mock() // used for getting current caching replicas

      // select pod record
      val sss: SelectSelectStep<Record> = mock()
      val sws: SelectConditionStep<Record> = mock()
      val result: Result<Record> = mock()
      val resultSet: ResultSet = mock()
      whenever(jooq.select()).thenReturn(sss)
      whenever(sss.from(any<TableLike<Record>>())).thenReturn(sjs).thenReturn(sjsExisting).thenReturn(sjsCurrent)
      whenever(sjs.where(any<Condition>())).thenReturn(sws)
      whenever(sws.fetch()).thenReturn(result)
      whenever(result.intoResultSet()).thenReturn(resultSet)

      // insert pod record
      val iss: InsertSetStep<Record> = mock()
      val ivsColumns: InsertValuesStep2<Record, Any, Any> = mock()
      val ivsValues: InsertValuesStep2<Record, Any, Any> = mock()
      whenever(jooq.insertInto(any<Table<Record>>())).thenReturn(iss)
      whenever(iss.columns(any<Field<Any>>(), any<Field<Any>>())).thenReturn(ivsColumns)
      whenever(ivsColumns.values(eq("node1"), any())).thenReturn(ivsValues)
      whenever(ivsValues.execute()).thenReturn(0)

      // select existing replicas
      val resultExisting: Result<Record> = mock()
      val resultSetExisting: ResultSet = mock()
      whenever(jooq.select()).thenReturn(sss)
      whenever(sjsExisting.fetch()).thenReturn(resultExisting)
      whenever(resultExisting.intoResultSet()).thenReturn(resultSetExisting)
      whenever(resultSetExisting.next()).thenReturn(false)

      // select current replicasv
      val orderCurrent: SelectSeekStep1<Record, Any> = mock()
      val resultCurrent: Result<Record> = mock()
      val resultSetCurrent: ResultSet = mock()
      whenever(sjsCurrent.orderBy(any<OrderField<Any>>())).thenReturn(orderCurrent)
      whenever(orderCurrent.fetch()).thenReturn(resultCurrent)
      whenever(resultCurrent.intoResultSet()).thenReturn(resultSetCurrent)
      whenever(resultSetCurrent.next()).thenReturn(true).thenReturn(false)
      whenever(resultSetCurrent.getString(POD_ID)).thenReturn("node1")
    }
  }
}
