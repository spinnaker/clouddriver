package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.google.common.collect.Lists
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern
import java.util.stream.Collectors

class DeployDcosServerGroupDescriptionToAppMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeployDcosServerGroupDescriptionToAppMapper)
    private static final VIP_PATTERN = Pattern.compile("VIP_\\d*")

    public App map(final String resolvedAppName, final DeployDcosServerGroupDescription description) {
        new App().with {
            id = resolvedAppName
            instances = description.desiredCapacity
            cpus = description.cpus
            mem = description.mem
            disk = description.disk
            gpus = description.gpus
            container = new Container().with {
                if (description.docker) {
                    docker = new Docker().with {
                        image = description.docker.image.imageId
                        network = description.networkType.type
                        portMappings = parsePortMappings(resolvedAppName, description.serviceEndpoints)
                        privileged = description.docker.privileged
                        parameters = description.docker.parameters.stream().map({ parameter ->
                            new Parameter().with {
                                key = parameter.key
                                value = parameter.value
                                it
                            }
                        }).collect(Collectors.toList())
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
            version = description.version
            residency = description.residency
            taskKillGracePeriodSeconds = description.taskKillGracePeriodSeconds
            secrets = description.secrets
            requirePorts = description.requirePorts
            acceptedResourceRoles = description.acceptedResourceRoles

            if (description.networkType != null && "BRIDGE" == description.networkType.type) {
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
            constraintGroup -> if (constraintGroup.size() != 3) {
                throw new RuntimeException("Given constraint [${constraintGroup.join(':')}] was invalid as it had ${constraintGroup.size()} parts instead of the expected 3.")
            }
        })

        parsedConstraints
    }

    private Tuple parsePortMappingLabels(String appId, DeployDcosServerGroupDescription.ServiceEndpoint serviceEndpoint, int counter) {
        Map<String, String> parsedLabels = serviceEndpoint.labels.clone()

        List<String> vipKeys = parsedLabels.keySet().stream().filter({ key -> key.matches(VIP_PATTERN) })
                .collect(Collectors.toList())

        vipKeys.stream().forEach({
            key ->
                String vip = parsedLabels.remove(key)
                parsedLabels.put("VIP_${counter++}".toString(), vip)
        })

        if (serviceEndpoint.loadBalanced && vipKeys.isEmpty()) {
            parsedLabels.put("VIP_${counter++}".toString(), "${appId}:${serviceEndpoint.port}".toString())
        }

        return [counter, parsedLabels]
    }

    public List<Port> parsePortMappings(String appId, List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {

        int counter = 0

        serviceEndpoints.stream().map({
            serviceEndpoint -> new Port().with {
                protocol = serviceEndpoint.protocol
                containerPort = serviceEndpoint.port
                hostPort = null
                servicePort = null
                (counter, labels) = parsePortMappingLabels(appId, serviceEndpoint, counter)

                it
            }
        }).collect(Collectors.toList())
    }

    public List<PortDefinition> parsePortDefinitions(List<DeployDcosServerGroupDescription.ServiceEndpoint> serviceEndpoints) {
        def portDefinitions = serviceEndpoints.stream().map({
            serviceEndpoint -> new PortDefinition().with {
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
