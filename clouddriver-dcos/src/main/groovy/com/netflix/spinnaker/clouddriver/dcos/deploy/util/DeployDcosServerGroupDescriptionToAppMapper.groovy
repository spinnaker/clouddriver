package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.google.common.collect.Lists
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

import static com.google.common.base.Strings.emptyToNull

class DeployDcosServerGroupDescriptionToAppMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeployDcosServerGroupDescriptionToAppMapper)

    public App map(final String resolvedAppName, final DeployDcosServerGroupDescription description) {
        new App().with {
            id = resolvedAppName
            instances = description.desiredCapacity
            cpus = description.cpus
            mem = description.mem
            disk = description.disk
            gpus = description.gpus

            if (description.networkType == "USER" && emptyToNull(description.networkName)) {
              ipAddress = new AppIpAddress().with {
                networkName = description.networkName
                it
              }
            }

            container = new Container().with {
                if (description.docker) {
                    docker = new Docker().with {
                        image = description.docker.image.imageId
                        network = description.networkType

                        portMappings = parsePortMappings(resolvedAppName, description.serviceEndpoints)
                        privileged = description.docker.privileged
                        parameters = description.docker.parameters?.entrySet()?.stream()?.map({ entry ->
                            new Parameter().with {
                                key = entry.key
                                value = entry.value
                                it
                            }
                        })?.collect(Collectors.toList())
                        forcePullImage = description.docker.forcePullImage

                        it
                    }
                }
                //type = description.container.type
                volumes = parseVolumes(description.persistentVolumes, description.dockerVolumes, description.externalVolumes)

                it
            }

            env = description.env
            user = description.dcosUser
            cmd = description.cmd
            args = description.args
            constraints = parseConstraints(description.constraints)

            if (description.fetch) {
                fetch = description.fetch.stream().map({ fetchable ->
                    new Fetchable().with {
                        uri = fetchable.uri
                        cache = fetchable.cache
                        extract = fetchable.extract
                        executable = fetchable.executable
                        outputFile = fetchable.outputFile
                        it
                    }
                }).collect(Collectors.toList())
            }

            storeUrls = description.storeUrls
            backoffSeconds = description.backoffSeconds
            backoffFactor = description.backoffFactor
            maxLaunchDelaySeconds = description.maxLaunchDelaySeconds

            if (description.healthChecks) {
                healthChecks = description.healthChecks.stream().map({ healthCheck ->
                    new HealthCheck().with {
                        if (emptyToNull(healthCheck.command?.trim()) != null) {
                          command = new Command(value: healthCheck.command)
                        }
                        gracePeriodSeconds = healthCheck.gracePeriodSeconds
                        ignoreHttp1xx = healthCheck.ignoreHttp1xx
                        intervalSeconds = healthCheck.intervalSeconds
                        maxConsecutiveFailures = healthCheck.maxConsecutiveFailures
                        path = healthCheck.path
                        port = healthCheck.port
                        portIndex = healthCheck.portIndex
                        protocol = healthCheck.protocol
                        timeoutSeconds = healthCheck.timeoutSeconds
                        it
                    }
                }).collect(Collectors.toList())
            }

            readinessChecks = description.readinessChecks?.collect({ rc ->
              new ReadinessCheck().with {
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
            })

            dependencies = description.dependencies
            labels = description.labels
            version = description.version

            residency = description.residency ? new Residency().with {
              taskLostBehaviour = description.residency.taskLostBehaviour
              relaunchEscalationTimeoutSeconds = description.residency.relaunchEscalationTimeoutSeconds
              it
            } : null

            taskKillGracePeriodSeconds = description.taskKillGracePeriodSeconds
            secrets = description.secrets
            requirePorts = description.requirePorts
            acceptedResourceRoles = description.acceptedResourceRoles

            // TODO I don't think this is correct
            if (description.networkType == "BRIDGE") {
                portDefinitions = parsePortDefinitions(description.serviceEndpoints)
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

    public List<List<String>> parseConstraints(String constaints) {
        List<List<String>> parsedConstraints = new ArrayList<>()

        if (constaints == null || constaints.trim().isEmpty()) {
            return parsedConstraints
        }

        List<String> constraintGroups = constaints.split(',')

        if (constraintGroups.isEmpty()) {
            return parsedConstraints
        }

        constraintGroups.forEach({
            constraintGroup -> parsedConstraints.add(Lists.newArrayList(constraintGroup.split(':')))
        })

        parsedConstraints.forEach({
            constraintGroup -> if (constraintGroup.size() < 2 || constraintGroup.size() > 3) {
                throw new RuntimeException("Given constraint [${constraintGroup.join(':')}] was invalid as it had ${constraintGroup.size()} parts instead of the expected 2 or 3.")
            }
        })

        parsedConstraints
    }

    private Map<String, String> parsePortMappingLabels(String appId, DeployDcosServerGroupDescription.ServiceEndpoint serviceEndpoint, int index) {
        Map<String, String> parsedLabels = serviceEndpoint.labels.clone()

        if (serviceEndpoint.loadBalanced && !parsedLabels.containsKey("VIP_${index}".toString())) {
            parsedLabels.put("VIP_${index}".toString(), "${appId}:${serviceEndpoint.port}".toString())
        }

        return parsedLabels
    }

    public List<PortMapping> parsePortMappings(String appId, List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {

        serviceEndpoints.withIndex().collect({
            serviceEndpoint, index -> new PortMapping().with {
                name = serviceEndpoint.name
                protocol = serviceEndpoint.protocol
                containerPort = serviceEndpoint.port
                hostPort = null
                servicePort = null
                labels = parsePortMappingLabels(appId, serviceEndpoint, index)

                it
            }
        })
    }

    public List<PortDefinition> parsePortDefinitions(List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {
        def portDefinitions = serviceEndpoints.stream().map({
            serviceEndpoint -> new PortDefinition().with {
                name = serviceEndpoint.name
                protocol = serviceEndpoint.protocol
                labels = [:]
                port = serviceEndpoint.port
                it
            }
        }).collect(Collectors.toList())

        portDefinitions.isEmpty() ? null : portDefinitions
    }

    public List<Volume> parseVolumes(List<DeployDcosServerGroupDescription.PersistentVolume> persistentVolumes,
                                     List<DeployDcosServerGroupDescription.DockerVolume> dockerVolumes,
                                     List<DeployDcosServerGroupDescription.ExternalVolume> externalVolumes) {
        List<Volume> parsedVolumes = new ArrayList<>()

        persistentVolumes.forEach({
            persistentVolume ->  parsedVolumes.add(new PersistentLocalVolume().with {
                it.setPersistentLocalVolumeInfo(new PersistentLocalVolume.PersistentLocalVolumeInfo().with {
                    size = persistentVolume.persistent.size
                    it
                })
                containerPath = persistentVolume.containerPath
                mode = persistentVolume.mode
                it
            })
        })

        dockerVolumes.forEach({
            dockerVolume ->  parsedVolumes.add(new LocalVolume().with {
                hostPath = dockerVolume.hostPath
                containerPath = dockerVolume.containerPath
                mode = dockerVolume.mode
                it
            })
        })

        externalVolumes.forEach({
            externalVolume ->  parsedVolumes.add(new ExternalVolume().with {
                containerPath = externalVolume.containerPath
                mode = externalVolume.mode
                it
            })
        })

        parsedVolumes
    }
}
