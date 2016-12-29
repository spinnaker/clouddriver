# DC/OS Driver  Notes


## Atomic Operations

We need to define what each of these means in terms of DC/OS constructs.  
If we understand how Kubernetes implements these and how Kubernetes concepts map to
DC/OS concepts that should get us started.  The assumption is that any of these that Kubernetes
does not implement are unlikely to be needed by DC/OS as well.


### Server Group operations

#### CLONE_SERVER_GROUP
* Kubernetes: ?
* DCOS: ?

#### CREATE_SERVER_GROUP
* Kubernetes: creates a ReplicaSet
* DCOS: Create an application.

#### DISABLE_SERVER_GROUP
* Kubernetes: Disables a ReplicaSet
* DCOS: Suspend an application.

#### ENABLE_SERVER_GROUP
* Kubernetes: Enables a ReplicaSet
* DCOS: ?

#### DESTROY_SERVER_GROUP
* Kubernetes: ?
* DCOS: Destroy an application

#### RESIZE_SERVER_GROUP
* Kubernetes: ?
* DCOS: Scale up/down application

#### UPSERT_SERVER_GROUP_TAGS
* Kubernetes: ?
* DCOS: ?

#### UPDATE_LAUNCH_CONFIG
* Kubernetes: ?
* DCOS: ?

#### UPSERT_SCALING_POLICY
* Kubernetes: ?
* DCOS: ?

#### DELETE_SCALING_POLICY
* Kubernetes: ?
* DCOS: ?

#### MIGRATE_SERVER_GROUP
* Kubernetes: ?
* DCOS: ?

#### MIGRATE_CLUSTER_CONFIGURATIONS
* Kubernetes: ?
* DCOS: ?


### Instance operations

#### REBOOT_INSTANCES
* Kubernetes: ?
* DCOS: ?

#### TERMINATE_INSTANCES
* Kubernetes: ?
* DCOS: ?

#### TERMINATE_INSTANCE_AND_DECREMENT
* Kubernetes: ?
* DCOS: ?

#### ATTACH_CLASSIC_LINK_VPC
* Kubernetes: ?
* DCOS: ?

#### REGISTER_INSTANCES_WITH_LOAD_BALANCER
* Kubernetes: ?
* DCOS: ?

#### DEREGISTER_INSTANCES_FROM_LOAD_BALANCER
* Kubernetes: ?
* DCOS: ?

#### ENABLE_INSTANCES_IN_DISCOVERY
* Kubernetes: ?
* DCOS: ?

#### DISABLE_INSTANCES_IN_DISCOVERY
* Kubernetes: ?
* DCOS: ?

#### UPDATE_INSTANCES
* Kubernetes: ?
* DCOS: ?

#### DETACH_INSTANCES
* Kubernetes: ?
* DCOS: ?


### Load Balancer operations

#### DELETE_LOAD_BALANCER
* Kubernetes: ?
* DCOS: ?

#### UPSERT_LOAD_BALANCER
* Kubernetes: ?
* DCOS: ?

#### MIGRATE_LOAD_BALANCER
* Kubernetes: ?
* DCOS: ?


### Security Group operations

#### DELETE_SECURITY_GROUP
* Kubernetes: ?
* DCOS: ?

#### UPSERT_SECURITY_GROUP
* Kubernetes: ?
* DCOS: ?

#### MIGRATE_SECURITY_GROUP
* Kubernetes: ?
* DCOS: ?


### JobStatus operations

#### RUN_JOB
* Kubernetes: ?
* DCOS: ?

#### DESTROY_JOB
* Kubernetes: ?
* DCOS: ?

#### CLONE_JOB
* Kubernetes: ?
* DCOS: ?


### Image operations

#### UPSERT_IMAGE_TAGS
* Kubernetes: ?
* DCOS: ?


### Snapshot operations

#### SAVE_SNAPSHOT
* Kubernetes: N/A
* DCOS: N/A

#### RESTORE_SNAPSHOT
* Kubernetes: N/A
* DCOS: N/A

