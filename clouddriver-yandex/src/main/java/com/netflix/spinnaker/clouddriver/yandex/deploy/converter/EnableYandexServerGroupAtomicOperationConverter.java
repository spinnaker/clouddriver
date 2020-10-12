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
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.EnableDisableYandexServerGroupDescription;
import com.netflix.spinnaker.clouddriver.yandex.deploy.ops.EnableDisableYandexServerGroupAtomicOperation;
import java.util.Map;
import org.springframework.stereotype.Component;

@YandexOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component
@SuppressWarnings({"unchecked", "rawtypes"})
public class EnableYandexServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public EnableDisableYandexServerGroupAtomicOperation convertOperation(Map input) {
    return new EnableDisableYandexServerGroupAtomicOperation(convertDescription(input), false);
  }

  @Override
  public EnableDisableYandexServerGroupDescription convertDescription(Map input) {
    return ConverterHelper.convertDescription(
        input, this, EnableDisableYandexServerGroupDescription.class);
  }
}
