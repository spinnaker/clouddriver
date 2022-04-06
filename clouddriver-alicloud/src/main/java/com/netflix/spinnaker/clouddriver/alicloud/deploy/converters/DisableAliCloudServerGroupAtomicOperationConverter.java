/*
 * Copyright 2022 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.converters;

import com.netflix.spinnaker.clouddriver.alicloud.AliCloudOperation;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.DisableAliCloudServerGroupDescription;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.ops.DisableAliCloudServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@AliCloudOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("disableAliCloudServerGroupDescription")
public class DisableAliCloudServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

    private final ClientFactory clientFactory;

    @Autowired
    public DisableAliCloudServerGroupAtomicOperationConverter(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public AtomicOperation convertOperation(Map input) {
        return new DisableAliCloudServerGroupAtomicOperation(convertDescription(input), clientFactory);
    }

    @Override
    public DisableAliCloudServerGroupDescription convertDescription(Map input) {
        DisableAliCloudServerGroupDescription description =
            getObjectMapper().convertValue(input, DisableAliCloudServerGroupDescription.class);
        description.setCredentials(getCredentialsObject((String) input.get("credentials")));
        return description;
    }
}
