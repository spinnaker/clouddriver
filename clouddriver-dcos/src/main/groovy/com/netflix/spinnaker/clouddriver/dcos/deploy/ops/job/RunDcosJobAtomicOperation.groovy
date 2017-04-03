package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.LocalVolume
import mesosphere.metronome.client.model.v1.Artifact
import mesosphere.metronome.client.model.v1.Constraint
import mesosphere.metronome.client.model.v1.Docker
import mesosphere.metronome.client.model.v1.Job
import mesosphere.metronome.client.model.v1.JobRunConfiguration
import mesosphere.metronome.client.model.v1.JobSchedule
import mesosphere.metronome.client.model.v1.Placement
import mesosphere.metronome.client.model.v1.RestartPolicy

class RunDcosJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "RUN_JOB"

  final DcosClientProvider dcosClientProvider
  final RunDcosJobDescription description

  RunDcosJobAtomicOperation(DcosClientProvider dcosClientProvider, RunDcosJobDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    def jobName = description.general.id

    task.updateStatus BASE_PHASE, "Initializing creation of job ${jobName}."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials)

    if (!dcosClient.maybeJob(description.general.id).isPresent()) {
      task.updateStatus BASE_PHASE, "Job with id of ${jobName} does not exist, creating job."

      def job = mapDescriptionToJob(description)

      if (job.schedules) {
        dcosClient.createJobWithSchedules(job)
      } else {
        dcosClient.createJob(job)
      }

      task.updateStatus BASE_PHASE, "Job ${jobName} was successfully created."
    }

    task.updateStatus BASE_PHASE, "Triggering job ${jobName}..."

    def jobRun = dcosClient.triggerJobRun(jobName)

    task.updateStatus BASE_PHASE, "Job ${jobName} has been started."

    // TODO We will want to change location to use regions like apps once that is supported.
    return new DeploymentResult().with {
      deployedNames = [jobRun.id]
      deployedNamesByLocation[jobRun.jobId] = [jobRun.id]
      it
    }
  }

  static Job mapDescriptionToJob(RunDcosJobDescription jobDescription) {
    new Job().with {
      id = jobDescription.general.id
      it.description = jobDescription.general.description

      if (jobDescription.labels) {
        labels = jobDescription.labels.clone() as Map<String, String>
      }

      run = new JobRunConfiguration().with {
        if (jobDescription.general) {
          cpus = jobDescription.general.cpus
          //gpus = description.general.gpus
          mem = jobDescription.general.mem
          disk = jobDescription.general.disk
          cmd = jobDescription.general.cmd
        }

        maxLaunchDelay = jobDescription.maxLaunchDelay
        user = jobDescription.user

        if (jobDescription.constraints) {
          placement = new Placement().with {
            constraints = jobDescription.constraints.collect { constraint ->
              new Constraint().with {
                attribute = constraint.attribute
                operator = constraint.operator
                value = constraint.value
                it
              }
            }
            it
          }
        }

        if (jobDescription.restartPolicy) {
          restart = new RestartPolicy().with {
            activeDeadlineSeconds = jobDescription.restartPolicy.activeDeadlineSeconds
            policy = jobDescription.restartPolicy.policy
            it
          }
        }

        if (jobDescription.artifacts) {
          artifacts = jobDescription.artifacts.collect { artifact ->
            new Artifact().with {
              uri = artifact.uri
              cache = artifact.cache
              executable = artifact.executable
              extract = artifact.extract
              it
            }
          }
        }

        if (jobDescription.volumes) {
          volumes = jobDescription.volumes.collect { volume ->
            new LocalVolume().with {
              containerPath = volume.containerPath
              hostPath = volume.hostPath
              mode = volume.mode
              it
            }
          }
        }

        if (jobDescription.image) {
          docker = new Docker().with {
            image = jobDescription.image.imageId
            it
          }
        }

        if (jobDescription.env) {
          env = jobDescription.env.clone() as Map<String, String>
        }

        it
      }

      if (jobDescription.schedule) {
        addSchedule(new JobSchedule().with {
          id = jobDescription.schedule.id ? jobDescription.schedule.id : 'default'
          enabled = jobDescription.schedule.enabled
          cron = jobDescription.schedule.cron
          timezone = jobDescription.schedule.timezone
          startingDeadlineSeconds = jobDescription.schedule.startingDeadlineSeconds
          concurrencyPolicy = "ALLOW"
          it
        })
      }

      it
    }
  }
}
