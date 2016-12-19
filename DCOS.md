# DC/OS Driver  Notes

## Atomic Operations

We need to define what each of these means in terms of DC/OS constructs.  
If we understand how Kubernetes implements these and how Kubernetes concepts map to
DC/OS concepts that should get us started.  The assumption is that any of these that Kubernetes
does not implement are unlikely to be needed by DC/OS as well.

### Server Group operations

#### CLONE_SERVER_GROUP
#### CREATE_SERVER_GROUP

* Kubernetes: creates a ReplicaSet, which is similar to a Marathon application.


#### DISABLE_SERVER_GROUP
#### ENABLE_SERVER_GROUP
#### DESTROY_SERVER_GROUP
#### RESIZE_SERVER_GROUP
#### UPSERT_SERVER_GROUP_TAGS
#### UPDATE_LAUNCH_CONFIG
#### UPSERT_SCALING_POLICY
#### DELETE_SCALING_POLICY
#### MIGRATE_SERVER_GROUP
#### MIGRATE_CLUSTER_CONFIGURATIONS

### Instance operations

#### REBOOT_INSTANCES
#### TERMINATE_INSTANCES
#### TERMINATE_INSTANCE_AND_DECREMENT
#### ATTACH_CLASSIC_LINK_VPC
#### REGISTER_INSTANCES_WITH_LOAD_BALANCER
#### DEREGISTER_INSTANCES_FROM_LOAD_BALANCER
#### ENABLE_INSTANCES_IN_DISCOVERY
#### DISABLE_INSTANCES_IN_DISCOVERY
#### UPDATE_INSTANCES
#### DETACH_INSTANCES

### Load Balancer operations

#### DELETE_LOAD_BALANCER
#### UPSERT_LOAD_BALANCER
#### MIGRATE_LOAD_BALANCER

### Security Group operations

#### DELETE_SECURITY_GROUP
#### UPSERT_SECURITY_GROUP
#### MIGRATE_SECURITY_GROUP

### JobStatus operations

#### RUN_JOB
#### DESTROY_JOB
#### CLONE_JOB

### Image operations

#### UPSERT_IMAGE_TAGS

### Snapshot operations

#### SAVE_SNAPSHOT

Unused by Kubernetes.

#### RESTORE_SNAPSHOT

Unused by Kubernetes.
