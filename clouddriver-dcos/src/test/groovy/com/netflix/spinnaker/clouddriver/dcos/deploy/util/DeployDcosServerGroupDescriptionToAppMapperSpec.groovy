package com.netflix.spinnaker.clouddriver.dcos.deploy.util

import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosSpinnakerId
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DeployDcosServerGroupDescription
import mesosphere.marathon.client.model.v2.App
import spock.lang.Specification

class DeployDcosServerGroupDescriptionToAppMapperSpec extends Specification {
    private static final APPLICATION_NAME = new DcosSpinnakerId("spinnaker", "test", "api-test-something-v000")

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully'() {
        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.service.app, stack: APPLICATION_NAME.service.stack,
                freeFormDetails: APPLICATION_NAME.service.detail, desiredCapacity: 1, cpus: 1.0, mem: 1.0, gpus: 1.0,
                disk: 0.0, env: ["var": "val"], dcosUser: 'spinnaker', cmd: 'ps', args: ["-A"],
                constraints: "something:GROUP_BY:other,test:GROUP_BY:other", fetch: ["fetch"],
                storeUrls: [ "someUrl" ], backoffSeconds: 1, backoffFactor: 1.15, maxLaunchDelaySeconds: 3600,
                readinessChecks: [], dependencies: ["some-other-service-v000"], labels: ["key": "value"],
                version: "0000-00-00'T'00:00:00.000", residency: "idk", taskKillGracePeriodSeconds: 1,
                secrets: [ "secret": "this is super secret"], requirePorts: false,
                acceptedResourceRoles: ["slave_public"],
                dockerVolumes: [new DeployDcosServerGroupDescription.DockerVolume(containerPath: "path/to/container",
                    hostPath: "host/path/to/container", mode: "someMode")],
                externalVolumes: [new DeployDcosServerGroupDescription.ExternalVolume(external: new DeployDcosServerGroupDescription.ExternalStorage(name: "lkjlj", provider: "dvdi", options: ["dvdi/driver": "rexray"]), mode: "someMode")],
                persistentVolumes: [new DeployDcosServerGroupDescription.PersistentVolume(containerPath: "path/to/container",
                        persistent: new DeployDcosServerGroupDescription.PersistentStorage(size: 512), mode: "someMode")],
                networkType: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "Bridge"),
                docker: new DeployDcosServerGroupDescription.Docker(privileged: false, forcePullImage: true,
                        network: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "Bridge"),
                        image: new DeployDcosServerGroupDescription.Image(imageId: "some/image:latest"),
                        parameters: [new DeployDcosServerGroupDescription.Parameter(key: "param", value: "value")]),
                healthChecks: [new DeployDcosServerGroupDescription.HealthCheck(path: "/meta/health", protocol: "tcp",
                        portIndex: 8080, gracePeriodSeconds: 5, intervalSeconds: 30, maxConsecutiveFailures: 1,
                        ignoreHttp1xx: false)],
                serviceEndpoints: [new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8080, protocol: "tcp",
                        networkType: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "bridge"),
                        name: "HTTP", loadBalanced: false, exposeToHost: false),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8081, protocol: "tcp",
                        networkType: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "bridge"),
                        name: "HTTP", loadBalanced: true, exposeToHost: false),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8082, protocol: "tcp",
                        networkType: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "bridge"),
                        name: "HTTP", loadBalanced: false, exposeToHost: false, labels: ["VIP_1": "vip_override:8082", "label1": "non_vip_label"]),
                                   new DeployDcosServerGroupDescription.ServiceEndpoint(port: 8083, protocol: "tcp",
                        networkType: new DeployDcosServerGroupDescription.NetworkType(type: "BRIDGE", name: "bridge"),
                        name: "HTTP", loadBalanced: true, exposeToHost: false, labels: ["VIP_11": "vip_override:8083", "VIP_abc": "non_numeric_vip"])],
                upgradeStrategy: new DeployDcosServerGroupDescription.UpgradeStrategy(minimumHealthCapacity: 1,
                        maximumOverCapacity: 2))

        when:
        App app = new DeployDcosServerGroupDescriptionToAppMapper().map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.desiredCapacity
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.cmd == description.cmd
        app.args == description.args
        app.user == description.dcosUser
        app.fetch == description.fetch
        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds
        app.readinessChecks == description.readinessChecks
        app.dependencies == description.dependencies
        app.labels == description.labels
        app.version == description.version
        app.residency == description.residency
        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker.image == description.docker.image.imageId
        app.container.docker.network == description.docker.network.type
        app.container.docker.privileged == description.docker.privileged
        app.container.docker.forcePullImage == description.docker.forcePullImage

        def labels = [[:],
                      ["VIP_0":"/spinnaker/test/api-test-something-v000:8081"],
                      ["label1": "non_vip_label", "VIP_1":"vip_override:8082"],
                      ["VIP_abc": "non_numeric_vip", "VIP_2":"vip_override:8083"]]
        app.container.docker.portMappings.size() == description.serviceEndpoints.size()
        [app.container.docker.portMappings, description.serviceEndpoints, labels].transpose().forEach({ appPortMapping, descriptionPortMapping, label ->
            assert appPortMapping.containerPort == descriptionPortMapping.port
            assert appPortMapping.protocol == descriptionPortMapping.protocol
            assert appPortMapping.labels == label
        })

        app.container.docker.parameters.size() == description.docker.parameters.size()
        [app.container.docker.parameters, description.docker.parameters].transpose().forEach({ appParameter, descriptionParameter ->
            assert appParameter.key == descriptionParameter.key
            assert appParameter.value == descriptionParameter.value
        })

        def combinedVolumes = []
        combinedVolumes.addAll(description.persistentVolumes)
        combinedVolumes.addAll(description.dockerVolumes)
        combinedVolumes.addAll(description.externalVolumes)

        app.container.volumes.size() == combinedVolumes.size()
        [app.container.volumes, combinedVolumes].transpose().forEach({ appVolume, descriptionVolume ->
            assert appVolume.containerPath == descriptionVolume.containerPath
            assert appVolume.mode == descriptionVolume.mode
        })

        app.healthChecks.size() == description.healthChecks.size()
        [app.healthChecks, description.healthChecks].transpose().forEach({ appHealthChecks, descriptionHealthChecks ->
            assert appHealthChecks.command == descriptionHealthChecks.command
            assert appHealthChecks.gracePeriodSeconds == descriptionHealthChecks.gracePeriodSeconds
            assert appHealthChecks.ignoreHttp1xx == descriptionHealthChecks.ignoreHttp1xx
            assert appHealthChecks.intervalSeconds == descriptionHealthChecks.intervalSeconds
            assert appHealthChecks.maxConsecutiveFailures == descriptionHealthChecks.maxConsecutiveFailures
            assert appHealthChecks.path == descriptionHealthChecks.path
            assert appHealthChecks.portIndex == descriptionHealthChecks.portIndex
            assert appHealthChecks.protocol == descriptionHealthChecks.protocol
            assert appHealthChecks.timeoutSeconds == descriptionHealthChecks.timeoutSeconds
        })

        app.portDefinitions.size() == description.serviceEndpoints.size()
        [app.portDefinitions, description.serviceEndpoints].transpose().forEach({ appPortDefinition, descriptionPortDefinition ->
            assert appPortDefinition.protocol == descriptionPortDefinition.protocol
            assert appPortDefinition.port == descriptionPortDefinition.port
        })

        if (app.upgradeStrategy && description.upgradeStrategy) {
            assert app.upgradeStrategy.maximumOverCapacity == description.upgradeStrategy.maximumOverCapacity
            assert app.upgradeStrategy.minimumHealthCapacity == description.upgradeStrategy.minimumHealthCapacity
        }
    }

    void 'DeployDcosServerGroupAtomicOperation should deploy the DCOS service successfully with many fields left empty'() {
        given:
        DeployDcosServerGroupDescription description = new DeployDcosServerGroupDescription(
                application: APPLICATION_NAME.service.app, stack: APPLICATION_NAME.service.stack,
                freeFormDetails: APPLICATION_NAME.service.detail, desiredCapacity: 1, cpus: 1.0, mem: 1.0, gpus: 1.0, disk: 0.0)

        when:
        App app = new DeployDcosServerGroupDescriptionToAppMapper().map(APPLICATION_NAME.toString(), description)

        then:
        noExceptionThrown()
        app.instances == description.desiredCapacity
        app.cpus == description.cpus
        app.mem == description.mem
        app.gpus == description.gpus
        app.disk == description.disk
        app.env == description.env
        app.cmd == description.cmd
        app.args == description.args
        app.user == description.dcosUser
        app.fetch == description.fetch
        app.storeUrls == description.storeUrls
        app.backoffSeconds == description.backoffSeconds
        app.backoffFactor == description.backoffFactor
        app.maxLaunchDelaySeconds == description.maxLaunchDelaySeconds
        app.readinessChecks == description.readinessChecks
        app.dependencies == description.dependencies
        app.labels == description.labels
        app.version == description.version
        app.residency == description.residency
        app.taskKillGracePeriodSeconds == description.taskKillGracePeriodSeconds
        app.secrets == description.secrets
        app.requirePorts == description.requirePorts
        app.acceptedResourceRoles == description.acceptedResourceRoles
        app.container.docker == null && description.docker == null
        app.container.volumes.isEmpty() && description.dockerVolumes.isEmpty() && description.externalVolumes.isEmpty() && description.persistentVolumes.isEmpty()
        app.healthChecks == null && description.healthChecks.isEmpty()
        app.portDefinitions == null && description.serviceEndpoints.isEmpty()
        app.upgradeStrategy == null && description.upgradeStrategy == null
    }
}
