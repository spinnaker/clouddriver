/*
 * Copyright 2020 YANDEX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.yandex.deploy.converter;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.yandex.YandexOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.DeleteYandexLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.DeleteYandexLoadBalancerAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@YandexOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component
@SuppressWarnings({"unchecked", "rawtypes"})
public class DeleteYandexLoadBalancerAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public DeleteYandexLoadBalancerAtomicOperation convertOperation(Map input) {
    return new DeleteYandexLoadBalancerAtomicOperation(convertDescription(input));
  }

  @Override
  public DeleteYandexLoadBalancerDescription convertDescription(Map input) {
    return ConverterHelper.convertDescription(
        input, this, DeleteYandexLoadBalancerDescription.class);
  }
}
