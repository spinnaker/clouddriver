package com.netflix.spinnaker.clouddriver.dcos.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.DcosServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DeployDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

class DeployDcosServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  final DcosClientProvider dcosClientProvider
  final DeployDcosServerGroupDescription description

  DeployDcosServerGroupAtomicOperation(DcosClientProvider dcosClientProvider, DeployDcosServerGroupDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }


  /*
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  ["frontend-lb"],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "ports": [ { "containerPort": "80", "hostPort": "80", "name": "http", "protocol": "TCP", "hostIp": "10.239.18.11" } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "livenessProbe": { "handler": { "type": "EXEC", "execAction": { "commands": [ "ls" ] } } } } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "loadBalancers":  [],  "volumeSources": [ { "name": "storage", "type": "EMPTYDIR", "emptyDir": {} } ], "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx", "tag": "latest", "registry": "index.docker.io" }, "volumeMounts": [ { "name": "storage", "mountPath": "/storage", "readOnly": false } ] } ], "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "capacity": { "min": 1, "max": 5 }, "scalingPolicy": { "cpuUtilization": { "target": 40 } }, "account":  "my-kubernetes-account" } } ]' localhost:7002/kubernetes/ops
   * curl -X POST -H "Content-Type: application/json" -d  '[ {  "createServerGroup": { "application": "kub", "stack": "test",  "targetSize": "3", "securityGroups": [], "loadBalancers":  [],  "containers": [ { "name": "librarynginx", "imageDescription": { "repository": "library/nginx" } } ], "account":  "my-kubernetes-account", "deployment": { "enabled": "true" } } } ]' localhost:7002/kubernetes/ops
   */

  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of application."

    def serverGroupNameResolver = new DcosServerGroupNameResolver(dcosClientProvider.getDcosClient(description.credentials), null)

    task.updateStatus BASE_PHASE, "Looking up next sequence index"
    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, "", "", false)
    task.updateStatus BASE_PHASE, "Application name chosen to be ${serverGroupName}."

    task.updateStatus BASE_PHASE, "Building application..."
//    def replicaSet = KubernetesApiConverter.toReplicaSet(new ReplicaSetBuilder(), description, serverGroupName)
//
//    if (KubernetesApiConverter.hasDeployment(description)) {
//      replicaSet.spec.replicas = 0
//    }
//
//    serverGroup = credentials.apiAdaptor.createReplicaSet(namespace, replicaSet)

    task.updateStatus BASE_PHASE, "Deployed service ${serverGroupName}"

//    if (KubernetesApiConverter.hasDeployment(description)) {
//      if (!credentials.apiAdaptor.getDeployment(namespace, clusterName)) {
//        task.updateStatus BASE_PHASE, "Building deployment..."
//        credentials.apiAdaptor.createDeployment(namespace, ((DeploymentBuilder) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) new DeploymentBuilder(), description, replicaSetName)).build())
//      } else {
//        task.updateStatus BASE_PHASE, "Updating deployment..."
//        ((DoneableDeployment) KubernetesApiConverter.toDeployment((DeploymentFluentImpl) credentials.apiAdaptor.editDeployment(namespace, clusterName),
//          description,
//          replicaSetName)).done()
//      }
//      task.updateStatus BASE_PHASE, "Configured deployment"
//    }

    DeploymentResult deploymentResult = new DeploymentResult()

    //deploymentResult.serverGroupNames = Arrays.asList("${serverGroup.metadata.name}".toString())

    return deploymentResult
  }
}
