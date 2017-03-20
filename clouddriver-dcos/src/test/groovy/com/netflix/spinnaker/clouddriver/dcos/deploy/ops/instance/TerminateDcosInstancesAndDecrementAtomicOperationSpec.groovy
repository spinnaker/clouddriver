package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesAndDecrementDescription
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.model.DCOSAuthCredentials
import mesosphere.marathon.client.model.v2.GetTasksResponse
import mesosphere.marathon.client.model.v2.Result
import spock.lang.Specification

class TerminateDcosInstancesAndDecrementAtomicOperationSpec extends Specification {
    DCOS dcosClient = Mock(DCOS)

    DcosCredentials testCredentials = new DcosCredentials(
            'test', 'test', 'test', 'https://test.url.com', Config.builder().withCredentials(DCOSAuthCredentials.forUserAccount('user', 'pw')).build()
    )

    DcosClientProvider dcosClientProvider = Stub(DcosClientProvider) {
        getDcosClient(testCredentials) >> dcosClient
    }

    def setup() {
        Task task = Mock(Task)
        TaskRepository.threadLocalTask.set(task)
    }

    void 'TerminateDcosInstancesAndDecrementAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId and hostId.'() {
        setup:
            def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: [], force: true)
            def atomicOperation = new TerminateDcosInstancesAndDecrementAtomicOperation(dcosClientProvider, description)
        when:
            atomicOperation.operate([])

        then:
            noExceptionThrown()
            1 * dcosClient.deleteAppTasksAndScaleWithHost(_, _, _) >> new Result()
    }

    void 'TerminateDcosInstancesAndDecrementAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId and taskId.'() {
        setup:
        def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: ["TASK ONE"], force: true)
        def atomicOperation = new TerminateDcosInstancesAndDecrementAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteAppTasksAndScaleWithTaskId(_, _, _) >> new Result()
    }

    void 'TerminateDcosInstancesAndDecrementAtomicOperation should terminate the tasks and scale the DCOS service successfully when given taskIds.'() {
        setup:
        def description = new TerminateDcosInstancesAndDecrementDescription(credentials: testCredentials,
                appId: null, hostId: null, taskIds: ["TASK ONE"], force: true)
        def atomicOperation = new TerminateDcosInstancesAndDecrementAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteTaskAndScale(_, _) >> new GetTasksResponse()
    }
}
