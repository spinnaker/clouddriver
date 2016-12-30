# DC/OS Driver  Notes

## Atomic Operations

We need to define what each of these means in terms of DC/OS constructs.  
If we understand how Kubernetes/Titus implements these and how Kubernetes/Titus concepts map to
DC/OS concepts that should get us started.  The assumption is that any of these that Kubernetes/Titus
does not implement are unlikely to be needed by DC/OS as well.

### Server Group operations

#### CLONE_SERVER_GROUP
* Don't know that this is possible with DC/OS and Marathon.

#### CREATE_SERVER_GROUP
* Create an application.

#### DISABLE_SERVER_GROUP
* Could potentially be a shortcut for suspending a service.

#### ENABLE_SERVER_GROUP
* N/A

#### DESTROY_SERVER_GROUP
* Destroy an application

#### RESIZE_SERVER_GROUP
* Scale up/down application

#### UPSERT_SERVER_GROUP_TAGS
* ?

#### UPDATE_LAUNCH_CONFIG
* ?

#### UPSERT_SCALING_POLICY
* If DCOS ever gets autoscaling support, this could be useful.

#### DELETE_SCALING_POLICY
* If DCOS ever gets autoscaling support, this could be useful.

#### MIGRATE_SERVER_GROUP
* ?

#### MIGRATE_CLUSTER_CONFIGURATIONS
* Given our plan for usage of DCOS, is this even worth looking into?


### Instance operations

#### REBOOT_INSTANCES
* Could be useful for OPS.

#### TERMINATE_INSTANCES
* Could be useful for OPS.

#### TERMINATE_INSTANCE_AND_DECREMENT
* ?

#### ATTACH_CLASSIC_LINK_VPC
* ?

#### REGISTER_INSTANCES_WITH_LOAD_BALANCER
* Since DC/OS does this for us using labels and such, not sure how useful this is.

#### DEREGISTER_INSTANCES_FROM_LOAD_BALANCER
* Since DC/OS does this for us using labels and such, not sure how useful this is.

#### ENABLE_INSTANCES_IN_DISCOVERY
* Could potentially be useful for Alva?

#### DISABLE_INSTANCES_IN_DISCOVERY
* Could potentially be useful for Alva?

#### UPDATE_INSTANCES
* Pretty sure we want deployments to be used to update anything.

#### DETACH_INSTANCES
* Could be useful for OPS.


### Load Balancer operations

#### DELETE_LOAD_BALANCER
* Since DC/OS does this for us using labels and such, not sure how useful this is.

#### UPSERT_LOAD_BALANCER
* Since DC/OS does this for us using labels and such, not sure how useful this is.

#### MIGRATE_LOAD_BALANCER
* Since DC/OS does this for us using labels and such, not sure how useful this is.


### Security Group operations

#### DELETE_SECURITY_GROUP
* Do we even care about security groups for DCOS?

#### UPSERT_SECURITY_GROUP
* Do we even care about security groups for DCOS?

#### MIGRATE_SECURITY_GROUP
* Do we even care about security groups for DCOS?


### JobStatus operations

#### RUN_JOB
* ?

#### DESTROY_JOB
* ?

#### CLONE_JOB
* ?


### Image operations

#### UPSERT_IMAGE_TAGS
* ?


### Snapshot operations

#### SAVE_SNAPSHOT
* N/A

#### RESTORE_SNAPSHOT
* N/A

