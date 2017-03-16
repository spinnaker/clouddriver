package com.netflix.spinnaker.clouddriver.dcos.deploy.util.mapper

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.ExternalVolume
import mesosphere.marathon.client.model.v2.LocalVolume
import mesosphere.marathon.client.model.v2.PersistentLocalVolume

import static java.util.stream.Collectors.joining

class AppToDeployDcosServerGroupDescriptionMapper {
  static DeployDcosServerGroupDescription map(final App app, final String account) {

    def spinId = DcosSpinnakerAppId.parse(app.id, account).get()
    def names = spinId.serverGroupName

    def desc = new DeployDcosServerGroupDescription()
    desc.application = names.app
    desc.stack = names.stack
    desc.freeFormDetails = names.detail

    desc.region = spinId.unsafeRegion
    desc.cmd = app.cmd
    desc.args = app.args
    desc.dcosUser = app.user

    desc.env = app.env

    desc.desiredCapacity = app.instances
    desc.cpus = app.cpus
    desc.mem = app.mem
    desc.disk = app.disk
    desc.gpus = app.gpus
    desc.constraints = app.constraints?.stream()?.map({ constraintParts -> constraintParts.join(':') })?.collect(joining(','));

    desc.fetch = app.fetch?.collect({ f ->
      new DeployDcosServerGroupDescription.Fetchable().with {
        uri = f.uri
        cache = f.cache
        extract = f.extract
        executable = f.executable
        outputFile = f.outputFile
        it
      }
    })

    desc.storeUrls = app.storeUrls
    desc.backoffSeconds = app.backoffSeconds
    desc.backoffFactor = app.backoffFactor
    desc.maxLaunchDelaySeconds = app.maxLaunchDelaySeconds

    def serviceEndpoints = []

    if (app.container?.docker) {
      def appDocker = app.container.docker
      def imageDesc = DcosProviderUtils.buildImageDescription(appDocker.image)
      desc.docker = new DeployDcosServerGroupDescription.Docker().with {
        privileged = appDocker.privileged
        forcePullImage = appDocker.forcePullImage

        network = appDocker.network

        image = new DeployDcosServerGroupDescription.Image().with {
          imageId = appDocker.image
          registry = imageDesc.registry
          repository = imageDesc.repository
          tag = imageDesc.tag
          it
        }

        parameters = appDocker.parameters?.collectEntries { [(it.key): it.value] }
        it
      }

      // If portMappings are populated, go ahead and use them. Otherwise, portDefinitions should be populated.
      if (appDocker.portMappings && !appDocker.portMappings.empty) {

        serviceEndpoints.addAll(appDocker.portMappings.collect { pm ->
          new DeployDcosServerGroupDescription.ServiceEndpoint().with {
            networkType = appDocker.network
            name = pm.name
            port = pm.containerPort
            protocol = pm.protocol
            labels = pm.labels
            loadBalanced = pm.labels?.keySet()?.any { it.startsWith('VIP') } ?: false
            exposeToHost = networkType == 'USER' && pm.hostPort != null && pm.hostPort == 0
            it
          }
        })
      }
    }

    // Wasn't populated from docker portMappings, so gotta be portDefinitions
    if (serviceEndpoints.empty && app.portDefinitions) {

      serviceEndpoints.addAll(app.portDefinitions.collect() { pd ->
        new DeployDcosServerGroupDescription.ServiceEndpoint().with {
          networkType = 'HOST'
          name = pd.name
          port = pd.port
          protocol = pd.protocol
          labels = pd.labels
          loadBalanced = pd.labels?.keySet()?.any { it.startsWith('VIP') } ?: false
          exposeToHost = false
          it
        }
      })
    }

    desc.serviceEndpoints = serviceEndpoints

    desc.healthChecks = app.healthChecks?.collect { hc ->
      new DeployDcosServerGroupDescription.HealthCheck().with {
        protocol = hc.protocol
        path = hc.path
        command = hc.command?.value
        portIndex = hc.portIndex
        port = hc.port
        gracePeriodSeconds = hc.gracePeriodSeconds
        intervalSeconds = hc.intervalSeconds
        timeoutSeconds = hc.timeoutSeconds
        maxConsecutiveFailures = hc.maxConsecutiveFailures
        ignoreHttp1xx = hc.ignoreHttp1xx
        it
      }
    }

    desc.readinessChecks = app.readinessChecks?.collect { rc ->
      new DeployDcosServerGroupDescription.ReadinessCheck().with {
        name = rc.name
        protocol = rc.protocol
        path = rc.path
        portName = rc.portName
        intervalSeconds = rc.intervalSeconds
        timeoutSeconds = rc.timeoutSeconds
        httpStatusCodesForReady = rc.httpStatusCodesForReady
        preserveLastResponse = rc.preserveLastResponse
        it
      }
    }

    desc.dependencies = app.dependencies

    if (app.upgradeStrategy) {
      desc.upgradeStrategy = new DeployDcosServerGroupDescription.UpgradeStrategy(
        minimumHealthCapacity: app.upgradeStrategy.minimumHealthCapacity,
        maximumOverCapacity: app.upgradeStrategy.maximumOverCapacity)
    }

    desc.labels = app.labels
    desc.acceptedResourceRoles = app.acceptedResourceRoles

    if (app.residency) {
      desc.residency = new DeployDcosServerGroupDescription.Residency().with {
        taskLostBehaviour = app.residency.taskLostBehaviour
        relaunchEscalationTimeoutSeconds = app.residency.relaunchEscalationTimeoutSeconds
        it
      }
    }

    desc.taskKillGracePeriodSeconds = app.taskKillGracePeriodSeconds
    desc.secrets = app.secrets

    desc.persistentVolumes = []
    desc.dockerVolumes = []
    desc.externalVolumes = []
    app.container?.volumes?.each {
      switch (it) {
        case LocalVolume:
          desc.dockerVolumes.add(new DeployDcosServerGroupDescription.DockerVolume(hostPath: ((LocalVolume) it).hostPath,
            containerPath: it.containerPath, mode: it.mode))
          break
        case ExternalVolume:
          def v = (ExternalVolume) it
          desc.externalVolumes.add(new DeployDcosServerGroupDescription.ExternalVolume().with {
            // TODO not really sure how to set "mode" and "options" on the ExternalStorage model (issue #200)
            external = new DeployDcosServerGroupDescription.ExternalStorage(name: v.name, provider: v.provider)
            containerPath = v.containerPath
            mode = v.mode
            it
          })
          break
        case PersistentLocalVolume:
          def v = (PersistentLocalVolume) it
          desc.persistentVolumes.add(new DeployDcosServerGroupDescription.PersistentVolume().with {
            persistent = new DeployDcosServerGroupDescription.PersistentStorage(size: v.persistentLocalVolumeInfo?.size)
            containerPath = v.containerPath
            mode = v.mode
            it
          })
          break
      }
    }

    desc.networkType = desc.docker?.network
    desc.requirePorts = app.requirePorts

    desc
  }
}
