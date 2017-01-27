package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.Container
import mesosphere.marathon.client.model.v2.Docker
import mesosphere.marathon.client.model.v2.HealthCheck
import mesosphere.marathon.client.model.v2.Parameter
import mesosphere.marathon.client.model.v2.PortDefinition
import mesosphere.marathon.client.model.v2.PortMapping
import mesosphere.marathon.client.model.v2.UpgradeStrategy
import mesosphere.marathon.client.model.v2.Volume

import java.util.stream.Collectors

class DeployDcosServerGroupDescriptionToAppMapper {
    public App map(final String resolvedAppName, final DeployDcosServerGroupDescription description) {
        new App().with {
            id = resolvedAppName
            instances = description.instances
            cpus = description.cpus
            mem = description.mem
            disk = description.disk
            gpus = description.gpus
            container = new Container().with {
                docker = new Docker().with {
                    image = description.container.docker.image
                    network = description.container.docker.network
                    portMappings = description.container.docker.portMappings.stream().map({ portMapping ->
                        new PortMapping().with {
                            containerPort = portMapping.containerPort
                            hostPort = portMapping.hostPort
                            servicePort = portMapping.servicePort
                            protocol = portMapping.protocol
                            labels = portMapping.labels
                            it
                        }
                    }).collect(Collectors.toList())
                    privileged = description.container.docker.privileged
                    parameters = description.container.docker.parameters.stream().map({ parameter ->
                        new Parameter().with {
                            key = parameter.key
                            value = parameter.value
                            it
                        }
                    }).collect(Collectors.toList())
                    forcePullImage = description.container.docker.forcePullImage

                    it
                }
                type = description.container.type
                volumes = description.container.volumes.stream().map({ volume ->
                    new Volume().with {
                        containerPath = volume.containerPath
                        hostPath = volume.hostPath
                        mode = volume.mode
                        it
                    }
                }).collect(Collectors.toList())

                it
            }

            env = description.env
            user = description.user
            cmd = description.cmd
            args = description.args
            constraints = description.constraints
            fetch = description.fetch
            storeUrls = description.storeUrls
            backoffSeconds = description.backoffSeconds
            backoffFactor = description.backoffFactor
            maxLaunchDelaySeconds = description.maxLaunchDelaySeconds

            if (description.healthChecks) {
                healthChecks = description.healthChecks.stream().map({ healthCheck ->
                    new HealthCheck().with {
                        command = healthCheck.command
                        gracePeriodSeconds = healthCheck.gracePeriodSeconds
                        ignoreHttp1xx = healthCheck.ignoreHttp1xx
                        intervalSeconds = healthCheck.intervalSeconds
                        maxConsecutiveFailures = healthCheck.maxConsecutiveFailures
                        path = healthCheck.path
                        portIndex = healthCheck.portIndex
                        protocol = healthCheck.protocol
                        timeoutSeconds = healthCheck.timeoutSeconds
                        it
                    }
                }).collect(Collectors.toList())
            }

            readinessChecks = description.readinessChecks
            dependencies = description.dependencies
            labels = description.labels
            ipAddress = description.ipAddress
            version = description.version
            residency = description.residency
            taskKillGracePeriodSeconds = description.taskKillGracePeriodSeconds
            secrets = description.secrets
            ports = description.ports
            requirePorts = description.requirePorts
            acceptedResourceRoles = description.acceptedResourceRoles

            if (description.portDefinitions) {
                portDefinitions = description.portDefinitions.stream().map({ portDefinition ->
                    new PortDefinition().with {
                        protocol = portDefinition.protocol
                        labels = portDefinition.labels
                        port = portDefinition.port
                        it
                    }
                }).collect(Collectors.toList())
            }

            if (description.upgradeStrategy) {
                upgradeStrategy = new UpgradeStrategy().with {
                    maximumOverCapacity = description.upgradeStrategy.maximumOverCapacity
                    minimumHealthCapacity = description.upgradeStrategy.minimumHealthCapacity
                    it
                }
            }

            it
        }
    }
}
