/*
 * Copyright 2021 Armory
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
import com.netflix.spinnaker.cats.agent.AgentExecution
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.NodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.sql.cluster.SqlClusteredAgentScheduler
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.nhaarman.mockito_kotlin.*
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.jooq.*
import org.mockito.stubbing.Answer
import java.sql.ResultSet
import java.util.concurrent.*

class SqlClusteredAgentSchedulerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("should shuffle agents") {
      whenever(dynamicConfigService.getConfig(eq(Int::class.java), eq("sql.agent.max-concurrent-agents"),
        any())).thenReturn(2)
      val agentExec1 = scheduleAgent("account1/KubernetesCoreCachingAgent[1/4]")
      val agentExec2 = scheduleAgent("account1/KubernetesCoreCachingAgent[2/4]")
      val agentExec3 = scheduleAgent("account1/KubernetesCoreCachingAgent[3/4]")
      val agentExec4 = scheduleAgent("account1/KubernetesCoreCachingAgent[4/4]")

      this.sqlClusteredAgentScheduler.run()
      this.sqlClusteredAgentScheduler.run()
      this.sqlClusteredAgentScheduler.run()
      this.sqlClusteredAgentScheduler.run()

      verify(agentExec1, atLeastOnce()
        .description("Agent account1/KubernetesCoreCachingAgent[1/4] never ran")).executeAgent(any())
      verify(agentExec2, atLeastOnce()
        .description("Agent account1/KubernetesCoreCachingAgent[2/4] never ran")).executeAgent(any())
      verify(agentExec3, atLeastOnce()
        .description("Agent account1/KubernetesCoreCachingAgent[3/4] never ran")).executeAgent(any())
      verify(agentExec4, atLeastOnce()
        .description("Agent account1/KubernetesCoreCachingAgent[4/4] never ran")).executeAgent(any())
    }
  }

  private inner class Fixture {
    val jooq: DSLContext = mock()
    val nodeIdentity: NodeIdentity = mock()
    val intervalProvider: AgentIntervalProvider = mock()
    val nodeStatusProvider: NodeStatusProvider = mock()
    val dynamicConfigService: DynamicConfigService = mock()
    val enabledAgentPattern = ".*"
    val disabledAgentsConfig = emptyList<String>()
    val agentLockInterval = 1L
    val tableNamespace = ""
    val agentExecutionPool: ExecutorService = mock()
    val lockPollingScheduler: ScheduledExecutorService = mock()
    val interval = AgentIntervalProvider.Interval(30L, 30L)
    val sqlClusteredAgentScheduler = SqlClusteredAgentScheduler(
      jooq,
      nodeIdentity,
      intervalProvider,
      nodeStatusProvider,
      dynamicConfigService,
      enabledAgentPattern,
      disabledAgentsConfig,
      agentLockInterval,
      tableNamespace,
      agentExecutionPool,
      lockPollingScheduler
    )

    init {
      whenever(nodeStatusProvider.isNodeEnabled).thenReturn(true)
      whenever(nodeIdentity.nodeIdentity).thenReturn("node1")
      whenever(dynamicConfigService.getConfig(eq(String::class.java), eq("sql.agent.disabled-agents"),
        any())).thenReturn("")
      whenever(dynamicConfigService.getConfig(eq(Long::class.java), eq("sql.agent.release-threshold-ms"),
        any())).thenReturn(50000L)

      // empty agent locks in db
      val sss: SelectSelectStep<Record2<Any, Any>> = mock()
      val sjs: SelectJoinStep<Record2<Any, Any>> = mock()
      val result: Result<Record2<Any, Any>> = mock()
      val resultSet: ResultSet = mock()
      whenever(jooq.select(any<SelectField<Any>>(), any<SelectField<Any>>())).thenReturn(sss)
      whenever(sss.from(any<TableLike<Record>>())).thenReturn(sjs)
      whenever(sjs.fetch()).thenReturn(result)
      whenever(result.intoResultSet()).thenReturn(resultSet)
      whenever(resultSet.next()).thenReturn(false)

      val iss: InsertSetStep<Record> = mock()
      val ivsColumns: InsertValuesStep4<Record, Any, Any, Any, Any> = mock()
      val ivsValues: InsertValuesStep4<Record, Any, Any, Any, Any> = mock()
      whenever(jooq.insertInto(any<Table<Record>>())).thenReturn(iss)
      whenever(iss.columns(any<Field<Any>>(), any<Field<Any>>(), any<Field<Any>>(), any<Field<Any>>())).thenReturn(ivsColumns)
      whenever(ivsColumns.values(any(), eq("node1"), any(), any())).thenReturn(ivsValues)
      whenever(ivsValues.execute()).thenReturn(0)

      val dus: DeleteUsingStep<Record> = mock()
      val dcs: DeleteConditionStep<Record> = mock()
      whenever(jooq.delete(any<Table<Record>>())).thenReturn(dus)
      whenever(dus.where(any<Condition>())).thenReturn(dcs)

      whenever(intervalProvider.getInterval(any())).thenReturn(interval)
      whenever(agentExecutionPool.submit(any())).thenAnswer(Answer {
        val r: Runnable = it.getArgument(0)
        r.run()
        object: FutureTask<Runnable>({ r }) { }
      })
    }

    fun scheduleAgent(name: String): AgentExecution {
      val agent: Agent = mock()
      val agentExecution: AgentExecution = mock()
      whenever(agent.agentType).thenReturn(name)
      sqlClusteredAgentScheduler.schedule(agent, agentExecution, mock())
      return agentExecution
    }
  }
}
