package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification

class DeployDcosServerGroupDescriptionToAppMapperSpec extends Specification {
    private static final APPLICATION_NAME = new DcosSpinnakerId("spinnaker", "test", "api-test-something-v000")

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {
        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.service.app, stack: APPLICATION_NAME.service.stack,
                detail: APPLICATION_NAME.service.detail, instances: 1, cpus: 1.0, mem: 1.0, gpus: 1.0, disk: 0.0,
                env: ["var": "val"], user: 'spinnaker', cmd: 'ps', args: ["-A"],
                constraints: [["something", 'GROUP_BY', "other"]], fetch: ["fetch"],
                storeUrls: [ "someUrl" ], backoffSeconds: 1, backoffFactor: 1.15, maxLaunchDelaySeconds: 3600,
                readinessChecks: [], dependencies: ["some-other-service-v000"], labels: ["key": "value"],
                ipAddress: "1.15.13.37", version: "0000-00-00'T'00:00:00.000", residency: "idk",
                taskKillGracePeriodSeconds: 1, secrets: [ "secret": "this is super secret"], ports: [ 8080 ],
                requirePorts: false, acceptedResourceRoles: ["slave_public"],
                container: new DeployDcosServerGroupDescription.Container(type: "DOCKER",
                        volumes: [new DeployDcosServerGroupDescription.Volume(containerPath: "path/to/container",
                                hostPath: "host/path/to/container", mode: "someMode")],
                        docker: new DeployDcosServerGroupDescription.Docker(image: "some/image:latest",
                                network: "BRIDGED", privileged: false, forcePullImage: true,
                                portMappings: [new DeployDcosServerGroupDescription.PortMapping(containerPort: 8080,
                                        hostPort: 12345, protocol: "someProtocol", labels: ["label": "value"],)],
                                parameters: [new DeployDcosServerGroupDescription.Parameter(key: "param", value: "value")])),
                healthChecks: [new DeployDcosServerGroupDescription.HealthCheck(path: "/meta/health", protocol: "tcp",
                        portIndex: 8080, gracePeriodSeconds: 5, intervalSeconds: 30, maxConsecutiveFailures: 1,
                        ignoreHttp1xx: false)],
                portDefinitions: [new DeployDcosServerGroupDescription.PortDefinition(port: 8080, protocol: "tcp",
                        labels: [ "VIP_0": "vip_address"])],
                upgradeStrategy: new DeployDcosServerGroupDescription.UpgradeStrategy(minimumHealthCapacity: 1,
                        maximumOverCapacity: 2))

        when:
        App app = DeployDcosServerGroupDescriptionToAppMapper.map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.instances
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.user == description.user
        app.cmd == description.cmd
        app.args == description.args
        app.constraints == description.constraints
        app.fetch == description.fetch
        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds
        app.readinessChecks == description.readinessChecks
        app.dependencies == description.dependencies
        app.labels == description.labels
        app.ipAddress == description.ipAddress
        app.version == description.version
        app.residency == description.residency
        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.ports == description.ports
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker.image == description.container.docker.image
        app.container.docker.network == description.container.docker.network
        app.container.docker.privileged == description.container.docker.privileged
        app.container.docker.forcePullImage == description.container.docker.forcePullImage

        app.container.docker.portMappings.size() == description.container.docker.portMappings.size()
        [app.container.docker.portMappings, description.container.docker.portMappings].transpose().forEach({ appPortMapping, descriptionPortMapping ->
            appPortMapping.containerPort == descriptionPortMapping.containerPort
            appPortMapping.hostPort == descriptionPortMapping.hostPort
            appPortMapping.servicePort == descriptionPortMapping.servicePort
            appPortMapping.protocol == descriptionPortMapping.protocol
            appPortMapping.labels == descriptionPortMapping.labels
        })

        app.container.docker.parameters.size() == description.container.docker.parameters.size()
        [app.container.docker.parameters, description.container.docker.parameters].transpose().forEach({ appParameter, descriptionParameter ->
            appParameter.key == descriptionParameter.key
            appParameter.value == descriptionParameter.value
        })

        app.container.type == description.container.type

        app.container.volumes.size() == description.container.volumes.size()
        [app.container.volumes, description.container.volumes].transpose().forEach({ appVolume, descriptionVolume ->
            appVolume.containerPath == descriptionVolume.containerPath
            appVolume.hostPath == descriptionVolume.hostPath
            appVolume.mode == descriptionVolume.mode
        })

        app.healthChecks.size() == description.healthChecks.size()
        [app.healthChecks, description.healthChecks].transpose().forEach({ appHealthChecks, descriptionHealthChecks ->
            appHealthChecks.command == descriptionHealthChecks.command
            appHealthChecks.gracePeriodSeconds == descriptionHealthChecks.gracePeriodSeconds
            appHealthChecks.ignoreHttp1xx == descriptionHealthChecks.ignoreHttp1xx
            appHealthChecks.intervalSeconds == descriptionHealthChecks.intervalSeconds
            appHealthChecks.maxConsecutiveFailures == descriptionHealthChecks.maxConsecutiveFailures
            appHealthChecks.path == descriptionHealthChecks.path
            appHealthChecks.portIndex == descriptionHealthChecks.portIndex
            appHealthChecks.protocol == descriptionHealthChecks.protocol
            appHealthChecks.timeoutSeconds == descriptionHealthChecks.timeoutSeconds
        })

        app.portDefinitions.size() == description.portDefinitions.size()
        [app.portDefinitions, description.portDefinitions].transpose().forEach({ appPortDefinition, descriptionPortDefinition ->
            appPortDefinition.protocol == descriptionPortDefinition.protocol
            appPortDefinition.labels == descriptionPortDefinition.labels
            appPortDefinition.port == descriptionPortDefinition.port
        })

        if (app.upgradeStrategy && description.upgradeStrategy) {
            app.upgradeStrategy.maximumOverCapacity == description.upgradeStrategy.maximumOverCapacity
            app.upgradeStrategy.minimumHealthCapacity == description.upgradeStrategy.minimumHealthCapacity
        }
    }

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully with many fields left empty'() {
        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.service.app, stack: APPLICATION_NAME.service.stack,
                detail: APPLICATION_NAME.service.detail, instances: 1, cpus: 1.0, mem: 1.0, gpus: 1.0, disk: 0.0,
                container: new DeployDcosServerGroupDescription.Container(
                        docker: new DeployDcosServerGroupDescription.Docker(image: "some/image:latest", network: "BRIDGED")))

        when:
        App app = DeployDcosServerGroupDescriptionToAppMapper.map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.instances
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.user == description.user
        app.cmd == description.cmd
        app.args == description.args
        app.constraints == description.constraints
        app.fetch == description.fetch
        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds
        app.readinessChecks == description.readinessChecks
        app.dependencies == description.dependencies
        app.labels == description.labels
        app.ipAddress == description.ipAddress
        app.version == description.version
        app.residency == description.residency
        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.ports == description.ports
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker.image == description.container.docker.image
        app.container.docker.network == description.container.docker.network
        app.container.docker.privileged == description.container.docker.privileged
        app.container.docker.forcePullImage == description.container.docker.forcePullImage

        if (app.container.docker.portMappings && description.container.docker.portMappings) {
            app.container.docker.portMappings.size() == description.container.docker.portMappings.size()
            [app.container.docker.portMappings, description.container.docker.portMappings].transpose().forEach({ appPortMapping, descriptionPortMapping ->
                appPortMapping.containerPort == descriptionPortMapping.containerPort
                appPortMapping.hostPort == descriptionPortMapping.hostPort
                appPortMapping.servicePort == descriptionPortMapping.servicePort
                appPortMapping.protocol == descriptionPortMapping.protocol
                appPortMapping.labels == descriptionPortMapping.labels
            })
        }

        app.container.type == description.container.type
        app.container.volumes.isEmpty() && description.container.volumes.isEmpty()
        app.container.docker.parameters.isEmpty() && description.container.docker.parameters.isEmpty()
        app.healthChecks.isEmpty() && description.healthChecks.isEmpty()
        app.portDefinitions == null && description.portDefinitions.isEmpty()
        app.upgradeStrategy == null && description.upgradeStrategy == null
    }
}
