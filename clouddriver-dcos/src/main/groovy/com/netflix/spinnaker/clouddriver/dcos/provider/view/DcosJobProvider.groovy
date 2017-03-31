package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.model.DcosJobStatus
import com.netflix.spinnaker.clouddriver.model.JobProvider
import mesosphere.dcos.client.DCOSException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DcosJobProvider implements JobProvider<DcosJobStatus> {
  static final JOB_FRAMEWORK = "metronome"

  final String platform = DcosCloudProvider.ID

  private final DcosClientProvider dcosClientProvider

  @Autowired
  DcosJobProvider(DcosClientProvider dcosClientProvider) {
    this.dcosClientProvider = dcosClientProvider
  }

  @Override
  DcosJobStatus collectJob(String account, String location, String id) {
    def dcosClient = dcosClientProvider.getDcosClient(account)
    def jobResponse = dcosClient.getJob(location, ['activeRuns','history'])

    return new DcosJobStatus(jobResponse, id, account)
  }

  @Override
  Map<String, Object> getFileContents(String account, String location, String id, String fileName) {
    def dcosClient = dcosClientProvider.getDcosClient(account)
    def taskName = "${id}.${location}".toString()
    def masterState = dcosClient.getMasterState()

    def metronomeFramework = masterState.getFrameworks().find {
      framework -> (JOB_FRAMEWORK == framework.getName())
    }

    def jobTask = metronomeFramework.getCompleted_tasks().find {
      task -> (taskName == task.getName())
    }

    def agentState = dcosClient.getAgentState(jobTask.getSlave_id())

    metronomeFramework = agentState.getCompleted_frameworks().find {
      framework -> (JOB_FRAMEWORK == framework.getName())
    }

    def jobExecutor = metronomeFramework.getCompleted_executors().find {
      executor -> (jobTask.getId() == executor.getId())
    }

    def filePath = "/var/lib/mesos/slave/slaves/${jobTask.getSlave_id()}/frameworks/${jobTask.getFramework_id()}/executors/${jobExecutor.getId()}/runs/${jobExecutor.getContainer()}/${fileName}".toString()

    try {
      def file = dcosClient.getAgentSandboxFile(jobTask.getSlave_id(), filePath)

      if (filePath.contains(".json")) {
        def type = new TypeToken<Map<String, Object>>(){}.getType()

        return new Gson().fromJson(file, type)
      }

      def properties = new Properties()

      properties.load(new ByteArrayInputStream(file.getBytes()))

      return properties as Map<String, Object>
    } catch (DCOSException e) {
      if (e.status == 404) {
        return [:]
      } else {
        throw e
      }
    }
  }
}
