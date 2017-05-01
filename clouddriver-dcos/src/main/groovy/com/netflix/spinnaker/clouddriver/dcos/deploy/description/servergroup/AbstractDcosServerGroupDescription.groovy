package com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup;

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.AbstractDcosCredentialsDescription;

abstract class AbstractDcosServerGroupDescription extends AbstractDcosCredentialsDescription {
    String region
    String serverGroupName
}