/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.deploy.converters;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.UpsertAliCloudLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.ops.DeleteAliCloudLoadBalancerClassicAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DeleteAliCloudLoadBalancerAtomicOperationConverterTest extends CommonConverter {

  DeleteAliCloudLoadBalancerAtomicOperationConverter converter =
      new DeleteAliCloudLoadBalancerAtomicOperationConverter(clientFactory);

  @Before
  public void testBefore() {
    converter.setObjectMapper(new ObjectMapper());
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
  }

  @Test
  public void testConvertOperation() {
    AtomicOperation atomicOperation = converter.convertOperation(buildDescription());
    assertTrue(atomicOperation instanceof DeleteAliCloudLoadBalancerClassicAtomicOperation);
  }

  @Test
  public void testConvertDescription() {
    UpsertAliCloudLoadBalancerDescription upsertAliCloudLoadBalancerDescription =
        converter.convertDescription(buildDescription());
    assertTrue(
        upsertAliCloudLoadBalancerDescription instanceof UpsertAliCloudLoadBalancerDescription);
  }

  private Map buildDescription() {
    Map<String, Object> description = new HashMap<>();
    description.put("region", REGION);
    description.put("credentials", ACCOUNT);
    return description;
  }
}
