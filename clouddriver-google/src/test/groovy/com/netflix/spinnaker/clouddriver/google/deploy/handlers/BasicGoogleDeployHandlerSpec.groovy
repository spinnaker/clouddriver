/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers

import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceList
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.google.api.services.compute.model.Operation
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Namer
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployHandlerSpec extends Specification {

  @Shared
  BasicGoogleDeployHandler handler

  void setupSpec() {
    this.handler = new BasicGoogleDeployHandler()
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "handler supports basic deploy description type"() {
    given:
    def description = new BasicGoogleDeployDescription()

    expect:
    handler.handles description
  }

  /**
   * TODO: this is a really hard thing to test.
   */
  @Ignore
  void "handler deploys with netflix specific naming convention"() {
    setup:
    def compute = Mock(Compute)
    def instanceMock = getComputeMock(Compute.Instances, Compute.Instances.List, InstanceList, Instance, null)
    def credentials = new GoogleCredentials("project", compute)
    def description = new BasicGoogleDeployDescription(application: "app", stack: "stack", image: "image", instanceType: "f1-micro", zone: "us-central1-b", credentials: credentials)

    when:
    handler.handle(description, [])

    then:
    10 * compute.machineTypes() >> getComputeMock(Compute.MachineTypes, Compute.MachineTypes.List, MachineTypeList, MachineType, description.instanceType)
    10 * compute.images() >> getComputeMock(Compute.Images, Compute.Images.List, ImageList, Image, description.image)
    10 * compute.networks() >> getComputeMock(Compute.Networks, Compute.Networks.List, NetworkList, Network, "default")
    20 * compute.instances() >> instanceMock
    10 * instanceMock.insert(_, _, _) >> Mock(Compute.Instances.Insert)
  }

  def getItem(name, Class type) {
    [getName: { name }, getSelfLink: { "selfLink" }]
  }

  def getComputeMock(Class mockType, Class listType, Class listModelType, Class modelType, String name) {
    def mock = Mock(mockType)
    def list = Mock(listType)
    def listModel = Mock(ComputeRequest)
    listModel.getItems() >> [getItems: getItem(name, modelType)]
    list.execute() >> listModel
    mock.list(_, _) >> list
    mock
  }

  def "backend service update creates closure that returns operation"() {
    given:
    def mockCompute = Mock(Compute)
    def mockBackendServices = Mock(Compute.BackendServices)
    def mockGet = Mock(Compute.BackendServices.Get)
    def mockUpdate = Mock(Compute.BackendServices.Update)
    def mockOperation = new Operation(name: "operation-123", status: "DONE")
    
    // Setup registry mocks for timeExecute
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    def mockRegistry = Mock(Registry)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler()
    handler.registry = mockRegistry
    
    // Mock backend service data
    def existingBackendService = new BackendService(name: "test-backend-service", backends: [])
    def newBackendService = new BackendService(
        name: "test-backend-service",
        backends: [new Backend(group: "projects/test/zones/us-central1-a/instanceGroups/new-group")]
    )
    
    when:
    def updateClosure = handler.updateBackendServices(mockCompute, "test-project", "test-backend-service", newBackendService)
    def result = updateClosure.call()
    
    then:
    // Verify GCP API calls
    2 * mockCompute.backendServices() >> mockBackendServices
    1 * mockBackendServices.get("test-project", "test-backend-service") >> mockGet
    1 * mockGet.execute() >> existingBackendService
    1 * mockBackendServices.update("test-project", "test-backend-service", _) >> mockUpdate
    1 * mockUpdate.execute() >> mockOperation
    
    result == mockOperation
  }
    
  def "autoscaler operations return operations for waiting"() {
    setup:
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    def mockCompute = Mock(Compute)
    def mockAutoscalers = Mock(Compute.Autoscalers)
    def mockAutoscalerInsert = Mock(Compute.Autoscalers.Insert)
    def mockRegionAutoscalers = Mock(Compute.RegionAutoscalers)
    def mockRegAutoscalerInsert = Mock(Compute.RegionAutoscalers.Insert)
    
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def zonalOperation = new Operation(name: "zonal-op", status: "DONE")
    def regionalOperation = new Operation(name: "regional-op", status: "DONE")
    
    mockCompute.autoscalers() >> mockAutoscalers
    mockCompute.regionAutoscalers() >> mockRegionAutoscalers
    mockAutoscalers.insert(_, _, _) >> mockAutoscalerInsert
    mockAutoscalerInsert.execute() >> zonalOperation
    mockRegionAutoscalers.insert(_, _, _) >> mockRegAutoscalerInsert
    mockRegAutoscalerInsert.execute() >> regionalOperation
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    when:
    def zonalResult = handler.timeExecute(mockAutoscalerInsert, "compute.autoscalers.insert", "TAG_SCOPE", "SCOPE_ZONAL")
    def regionalResult = handler.timeExecute(mockRegAutoscalerInsert, "compute.regionAutoscalers.insert", "TAG_SCOPE", "SCOPE_REGIONAL")
    
    then:
    1 * mockAutoscalerInsert.execute() >> zonalOperation
    1 * mockRegAutoscalerInsert.execute() >> regionalOperation
    
    zonalResult.getName() == "zonal-op"
    regionalResult.getName() == "regional-op"
  }
}
