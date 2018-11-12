/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Index.atIndex;
import static org.mockito.Mockito.verify;

class DeleteCloudFoundryLoadBalancerAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  DeleteCloudFoundryLoadBalancerAtomicOperationTest() {
    super();
  }

  @Test
  void deleteLoadBalancer() {
    CloudFoundryLoadBalancer loadBalancer = CloudFoundryLoadBalancer.builder()
      .id("id")
      .host("host")
      .domain(CloudFoundryDomain.builder().name("mydomain").build())
      .build();

    DeleteCloudFoundryLoadBalancerDescription desc = new DeleteCloudFoundryLoadBalancerDescription();
    desc.setClient(client);
    desc.setLoadBalancer(loadBalancer);

    DeleteCloudFoundryLoadBalancerAtomicOperation op = new DeleteCloudFoundryLoadBalancerAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Deleting load balancer " + loadBalancer.getName()), atIndex(1))
      .has(status("Deleted load balancer " + loadBalancer.getName()), atIndex(2));

    verify(client.getRoutes()).deleteRoute(loadBalancer.getId());
  }

  @Test
  void nonExistentRoute() {
    DeleteCloudFoundryLoadBalancerDescription desc = new DeleteCloudFoundryLoadBalancerDescription();
    desc.setClient(client);
    desc.setLoadBalancer(null);

    DeleteCloudFoundryLoadBalancerAtomicOperation op = new DeleteCloudFoundryLoadBalancerAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Load balancer does not exist"), atIndex(1));
  }
}
