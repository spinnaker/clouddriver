/*
 * Copyright 2022 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ForceRestartServerGroupDescription;
import java.util.List;

public class ForceRestartServerGroupAtomicOperation
    extends AbstractEcsAtomicOperation<ForceRestartServerGroupDescription, Void> {

  public ForceRestartServerGroupAtomicOperation(ForceRestartServerGroupDescription description) {
    super(description, "FORCE_RESTART_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List<Void> priorOutputs) {
    updateTaskStatus("Initialising ForceRestart Amazon ECS server group operation..... ");
    updateService();
    return null;
  }

  public void updateService() {
    AmazonECS ecsClient = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getAccount();
    String cluster = getCluster(service, account);

    UpdateServiceRequest request =
        new UpdateServiceRequest()
            .withCluster(cluster)
            .withService(service)
            .withForceNewDeployment(true);

    updateTaskStatus(
        String.format("Forcing new deployment of %s server group for %s.", service, account));
    ecsClient.updateService(request);
    updateTaskStatus(String.format("Server group %s restarted for %s.", service, account));
  }
}
