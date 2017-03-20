package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.instance.TerminateDcosInstancesDescription
import mesosphere.dcos.client.Config
import mesosphere.dcos.client.DCOS
import mesosphere.dcos.client.model.DCOSAuthCredentials
import mesosphere.marathon.client.model.v2.DeleteAppTasksResponse
import mesosphere.marathon.client.model.v2.GetTasksResponse
import spock.lang.Specification

class TerminateDcosInstancesAtomicOperationSpec extends Specification {
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

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId, hostId, and false for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: [], force: true, wipe: false)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteAppTasksWithHost(_, _, _) >> new DeleteAppTasksResponse()
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId, hostId, and true for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: "192.168.0.0", taskIds: [], force: false, wipe: true)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteAppTasksAndWipeWithHost(_, _, _) >> new DeleteAppTasksResponse()
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId, taskId, and false for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: ["TASK ONE"], force: true, wipe: false)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteAppTasksWithTaskId(_, _, _) >> new DeleteAppTasksResponse()
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given an appId, taskId, and true for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: "test/region/app-stack-detail-v000", hostId: null, taskIds: ["TASK ONE"], force: false, wipe: true)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteAppTasksAndWipeWithTaskId(_, _, _) >> new DeleteAppTasksResponse()
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given taskIds and false for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: null, hostId: null, taskIds: ["TASK ONE", "TASK TWO"], force: true, wipe: false)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteTask(_, _) >> new GetTasksResponse()
    }

    void 'TerminateDcosInstancesAtomicOperation should terminate the tasks and scale the DCOS service successfully when given taskIds and true for wipe.'() {
        setup:
        def description = new TerminateDcosInstancesDescription(credentials: testCredentials,
                appId: null, hostId: null, taskIds: ["TASK ONE", "TASK TWO"], force: false, wipe: true)
        def atomicOperation = new TerminateDcosInstancesAtomicOperation(dcosClientProvider, description)
        when:
        atomicOperation.operate([])

        then:
        noExceptionThrown()
        1 * dcosClient.deleteTaskAndWipe(_, _) >> new GetTasksResponse()
    }
}
