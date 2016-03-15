/*
 * Copyright 2016 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.azure.templates

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription

@Slf4j
class AzureServerGroupResourceTemplate {

  protected static ObjectMapper mapper = new ObjectMapper()
    .configure(SerializationFeature.INDENT_OUTPUT, true)
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

  /**
   * Build the resource manager template that will create the Azure equivalent (VM Scale Set)
   * of the Spinnaker Server Group
   * @param description - Description object containing the values to be specified in the template
   * @return - JSON string representing the Resource Manager template for a Azure VM Scale Set (Server Group)
   */
  static String getTemplate(AzureServerGroupDescription description) {
    ServerGroupTemplate template = new ServerGroupTemplate(description)
    mapper.writeValueAsString(template)
  }

  /**
   *
   */
  static class ServerGroupTemplate {
    String $schema = "https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#"
    String contentVersion = "1.0.0.0"

    ServerGroupTemplateParameters parameters
    TemplateVariables variables
    ArrayList<Resource> resources = []

    /**
     *
     * @param description
     */
    ServerGroupTemplate(AzureServerGroupDescription description) {
      parameters = new ServerGroupTemplateParameters()

      if (description.image.isCustom) {
        variables = new EmptyTemplateVariables()
      } else {
        variables = new ServerGroupTemplateVariables(description)
        resources.add(new StorageAccount(description))
      }

      resources.add(new VirtualMachineScaleSet(description))
    }

  }

  interface TemplateVariables {}

  static class EmptyTemplateVariables implements TemplateVariables {}

  /**
   *
   */
  static class ServerGroupTemplateVariables implements TemplateVariables {
    // The values of these variables need to match the name of the corresponding variables below
    // The reason is that in other sections of the RM template (i.e., Resources) their is a reference by name
    // to the variable defined in Variables section of the template.
    // These *Var variables are meant to help keep that reference from breaking IF the name of the actual variable
    // changes
    static transient String uniqueStorageNamesArrayVar = 'uniqueStorageNameArray'
    static transient String newStorageAccountsSuffixVar = 'newStorageAccountSuffix'
    static transient String vhdContainerNameVar = 'vhdContainerName'

    String newStorageAccountSuffix
    String vhdContainerName
    OsType osType
    String imageReference
    ArrayList<String> uniqueStorageNameArray = []

    /**
     *
     * @param description
     */
    ServerGroupTemplateVariables(AzureServerGroupDescription description) {

      newStorageAccountSuffix = "sa"
      vhdContainerName = description.getIdentifier().toLowerCase()
      osType = new OsType(description)
      imageReference = "[variables('osType')]"

      String noDashName = description.name.replaceAll("-", "").toLowerCase()

      for (int i = 0; i < description.getStorageAccountCount(); i++) {
        String uniqueName = "[concat(uniqueString(concat(resourceGroup().id, subscription().id, '$noDashName', variables('$newStorageAccountsSuffixVar'), '$i')))]"
        uniqueStorageNameArray.add(uniqueName)
      }
    }
  }

  /**
   *
   */
  static class ServerGroupTemplateParameters {
    LocationParameter location = new LocationParameter()
    SubnetParameter subnetId = new SubnetParameter()
  }

  static class SubnetParameter {
    String type = "string"
    Map<String, String> metadata = ["description":"Subnet Resource ID"]
  }
  /**
   *
   */
  static class LocationParameter {
    String type = "string"
    Map<String, String> metadata = ["description":"Location to deploy"]
  }

  /**
   *
   */
  static class OsType {
    String publisher
    String offer
    String sku
    String version

    /**
     *
     * @param description
     */
    OsType(AzureServerGroupDescription description)
    {
      publisher = description.image.publisher
      offer = description.image.offer
      sku = description.image.sku
      version = description.image.version
    }
  }

  /**
   *
   */
  static class StorageAccount extends Resource {
    CopyOperation copy
    StorageAccountProperties properties

    /**
     *
     * @param description
     */
    StorageAccount(AzureServerGroupDescription description) {
      apiVersion = "2015-06-15"
      name = String.format("[concat(variables('%s')[copyIndex()], variables('%s'))]",
        ServerGroupTemplateVariables.uniqueStorageNamesArrayVar,
        ServerGroupTemplateVariables.newStorageAccountsSuffixVar)
      type = "Microsoft.Storage/storageAccounts"
      location = "[parameters('location')]"

      copy = new CopyOperation("storageLoop", description.getStorageAccountCount())
      tags = ["appName":description.application,
              "stack":description.stack,
              "detail":description.detail,
              "cluster":description.getClusterName()]
      properties = new StorageAccountProperties()
    }
  }

  /**
   *
   */
  static class StorageAccountProperties {
    String accountType

    /**
     *
     * @param description
     */
    StorageAccountProperties() {
      accountType = "Standard_LRS" // TODO get this from the description
    }
  }

  /**
   *
   */
  static class CopyOperation {
    String name
    int count

    /**
     *
     * @param operationName
     * @param iterations
     */
    CopyOperation(String operationName, int iterations) {
      name = operationName
      count = iterations
    }
  }

  /**
   *
   */
  static class VirtualMachineScaleSet extends DependingResource {
    ScaleSetSkuProperty sku
    VirtualMachineScaleSetProperty properties

    /**
     *
     * @param description
     */
    VirtualMachineScaleSet(AzureServerGroupDescription description) {
      apiVersion = "2015-06-15"
      name = description.getIdentifier()
      type = "Microsoft.Compute/virtualMachineScaleSets"
      location = "[parameters('location')]"
      tags = ["appName" : description.application,
              "stack" : description.stack,
              "detail" : description.detail,
              "cluster" : description.getClusterName()]

      if (!description.image.isCustom) {
        description.getStorageAccountCount().times { idx ->
          this.dependsOn.add(
            String.format("[concat('Microsoft.Storage/storageAccounts/', variables('%s')[%s], variables('%s'))]",
              ServerGroupTemplateVariables.uniqueStorageNamesArrayVar,
              idx,
              ServerGroupTemplateVariables.newStorageAccountsSuffixVar)
          )
        }
      }

      properties = new VirtualMachineScaleSetProperty(description)
      sku = new ScaleSetSkuProperty(description)
    }
  }

  static class VirtualMachineScaleSetProperty {
    Map<String, String> upgradePolicy = [:]
    ScaleSetVMProfileProperty virtualMachineProfile

    VirtualMachineScaleSetProperty(AzureServerGroupDescription description) {
      upgradePolicy["mode"] = description.upgradePolicy.toString()
      virtualMachineProfile = new ScaleSetVMProfileProperty(description)
    }
  }

  // ***Scale Set SKU
  /**
   *
   */
  static class ScaleSetSkuProperty {
    String name
    String tier
    int capacity

    /**
     *
     * @param description
     */
    ScaleSetSkuProperty(AzureServerGroupDescription description) {
      name = description.sku.name
      tier = description.sku.tier
      capacity = description.sku.capacity
    }
  }

  // ***OSProfile
  static class ScaleSetOsProfileProperty {
    String computerNamePrefix
    String adminUserName
    String adminPassword

    /**
     *
     * @param description
     */
    ScaleSetOsProfileProperty(AzureServerGroupDescription description) {
      //Max length of 10 characters to allow for an aditional postfix within a max length of 15 characters
      computerNamePrefix = description.getIdentifier().substring(0, 10)
      log.info("computerNamePrefix will be truncated to 10 characters to maintain Azure restrictions")
      adminUserName = description.osConfig.adminUserName
      adminPassword = description.osConfig.adminPassword
    }
  }

  // ***Network Profile
  /**
   *
   */
  static class ScaleSetNetworkProfileProperty {
    ArrayList<NetworkInterfaceConfiguration> networkInterfaceConfigurations = []

    /**
     *
     * @param description
     */
    ScaleSetNetworkProfileProperty(AzureServerGroupDescription description) {
      networkInterfaceConfigurations.add(new NetworkInterfaceConfiguration(description))
    }
  }

  /**
   *
   */
  static class NetworkInterfaceConfiguration {
    String name
    NetworkInterfaceConfigurationProperty properties

    /**
     *
     * @param description
     */
    NetworkInterfaceConfiguration(AzureServerGroupDescription description) {
      name = AzureUtilities.NETWORK_INTERFACE_PREFIX + description.getIdentifier()
      properties = new NetworkInterfaceConfigurationProperty(description)
    }
  }

  /**
   *
   */
  static class NetworkInterfaceConfigurationProperty {
    String primary
    ArrayList<NetworkInterfaceIPConfiguration> ipConfigurations = []

    /**
     *
     * @param description
     */
    NetworkInterfaceConfigurationProperty(AzureServerGroupDescription description) {
      primary = "true"
      ipConfigurations.add(new NetworkInterfaceIPConfiguration(description))
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfiguration {
    String name
    NetworkInterfaceIPConfigurationsProperty properties

    /**
     *
     * @param description
     */
    NetworkInterfaceIPConfiguration(AzureServerGroupDescription description) {
      name = AzureUtilities.IPCONFIG_NAME_PREFIX + description.getIdentifier()
      properties = new NetworkInterfaceIPConfigurationsProperty(description)
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfigurationsProperty {
    NetworkInterfaceIPConfigurationSubnet subnet
    ArrayList<LoadBalancerBackendAddressPool> loadBalancerBackendAddressPools = []

    /**
     *
     * @param description
     */
    NetworkInterfaceIPConfigurationsProperty(AzureServerGroupDescription description) {
      subnet = new NetworkInterfaceIPConfigurationSubnet()
      loadBalancerBackendAddressPools.add(new LoadBalancerBackendAddressPool(description))
    }
  }

  /**
   *
   */
  static class NetworkInterfaceIPConfigurationSubnet {
    String id
    NetworkInterfaceIPConfigurationSubnet() {
      id = "[parameters('subnetId')]"
    }
  }

  static class LoadBalancerBackendAddressPool {
    String id

    LoadBalancerBackendAddressPool(AzureServerGroupDescription description) {
      id = "[resourceId('Microsoft.Network/loadBalancers/backendAddressPools', '$description.loadBalancerName', 'be-$description.loadBalancerName')]"
    }
  }

  // ***VM Profile
  /**
   *
   */
  static class ScaleSetVMProfileProperty {
    StorageProfile storageProfile
    ScaleSetOsProfileProperty osProfile
    ScaleSetNetworkProfileProperty networkProfile

    ScaleSetVMProfileProperty(AzureServerGroupDescription description) {
      storageProfile = description.image.isCustom ?
        new ScaleSetCustomImageStorageProfile(description) :
        new ScaleSetStorageProfile(description)
      osProfile = new ScaleSetOsProfileProperty(description)
      networkProfile = new ScaleSetNetworkProfileProperty(description)
    }
  }

  interface OSDisk {
    String name
    String caching
    String createOption
  }

  interface StorageProfile {
    OSDisk osDisk
  }

  /**
   *
   */
  static class ScaleSetStorageProfile implements StorageProfile {

    OSDisk osDisk
    String imageReference
    /**
     *
     * @param serverGroupDescription
     */
    ScaleSetStorageProfile(AzureServerGroupDescription description) {
      osDisk = new VirtualMachineOSDisk(description)
      imageReference = "[variables('imageReference')]"
    }
  }

  /**
   *
   */
  static class ScaleSetCustomImageStorageProfile implements StorageProfile {

    OSDisk osDisk
    /**
     *
     * @param serverGroupDescription
     */
    ScaleSetCustomImageStorageProfile(AzureServerGroupDescription description) {
      osDisk = new VirtualMachineCustomImageOSDisk(description)
    }
  }

  static class VirtualMachineOSDisk implements OSDisk {

    String name
    String caching
    String createOption
    ArrayList<String> vhdContainers = []

    VirtualMachineOSDisk(AzureServerGroupDescription description) {
      name = "osdisk-" + description.getIdentifier()
      caching = "ReadOnly"
      createOption = "FromImage"
      description.getStorageAccountCount().times { idx ->
        vhdContainers.add(String.format("[concat('https://', variables('%s')[%s], variables('%s'), '.blob.core.windows.net/', variables('%s'))]",
          ServerGroupTemplateVariables.uniqueStorageNamesArrayVar,
          idx,
          ServerGroupTemplateVariables.newStorageAccountsSuffixVar,
          ServerGroupTemplateVariables.vhdContainerNameVar))
      }
    }
  }

  static class VirtualMachineCustomImageOSDisk implements OSDisk {

    String name
    String caching
    String createOption
    String osType
    Map<String, String> image = [:]

    VirtualMachineCustomImageOSDisk (AzureServerGroupDescription description) {
      name = "osdisk-${description.getIdentifier()}"
      caching = "ReadOnly"
      createOption = "FromImage"
      osType = description.image.ostype
      image.uri = description.image.uri
    }
  }
}
