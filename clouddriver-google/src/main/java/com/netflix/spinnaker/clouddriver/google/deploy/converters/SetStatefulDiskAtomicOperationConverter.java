/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters;

import com.netflix.spinnaker.clouddriver.google.GoogleOperation;
import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.SetStatefulDiskAtomicOperation;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@GoogleOperation(AtomicOperations.SET_STATEFUL_DISK)
@Component
public class SetStatefulDiskAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final GoogleClusterProvider clusterProvider;
  private final GoogleComputeApiFactory computeApiFactory;

  @Autowired
  public SetStatefulDiskAtomicOperationConverter(
      GoogleClusterProvider clusterProvider, GoogleComputeApiFactory computeApiFactory) {
    this.clusterProvider = clusterProvider;
    this.computeApiFactory = computeApiFactory;
  }

  @Override
  public SetStatefulDiskAtomicOperation convertOperation(Map input) {
    return new SetStatefulDiskAtomicOperation(
        clusterProvider, computeApiFactory, convertDescription(input));
  }

  @Override
  public SetStatefulDiskDescription convertDescription(Map input) {
    return GoogleAtomicOperationConverterHelper.convertDescription(
        input, this, SetStatefulDiskDescription.class);
  }
}
