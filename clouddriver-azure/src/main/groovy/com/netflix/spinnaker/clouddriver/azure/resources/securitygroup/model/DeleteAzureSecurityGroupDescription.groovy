package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model

import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription

class DeleteAzureSecurityGroupDescription extends AzureResourceOpsDescription{
  String securityGroupName
  List<String> regions
}
