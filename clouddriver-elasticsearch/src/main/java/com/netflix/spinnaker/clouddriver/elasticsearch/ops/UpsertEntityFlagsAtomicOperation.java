/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.EntityRefIdBuilder;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityFlagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.EntityFlag;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.security.AuthenticatedRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public class UpsertEntityFlagsAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "ENTITY_FLAGS";
  public static final String UI_FLAGS_KEY = "spinnaker_ui_flags";

  private final Front50Service front50Service;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final UpsertEntityFlagsDescription entityFlagsDescription;

  public UpsertEntityFlagsAtomicOperation(Front50Service front50Service,
                                          ElasticSearchEntityTagsProvider entityTagsProvider,
                                          UpsertEntityFlagsDescription entityFlagsDescription) {
    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
    this.entityFlagsDescription = entityFlagsDescription;
  }

  public Void operate(List priorOutputs) {
    EntityTags.EntityRef entityRef = entityFlagsDescription.getEntityRef();

    if (entityFlagsDescription.getId() == null) {
      EntityRefIdBuilder.EntityRefId entityRefId = EntityRefIdBuilder.buildId(
        entityRef.getCloudProvider(),
        entityRef.getEntityType(),
        entityRef.getEntityId(),
        (String) entityRef.attributes().get("account"),
        (String) entityRef.attributes().get("region")
      );
      entityFlagsDescription.setId(entityRefId.id);
      entityFlagsDescription.setIdPattern(entityRefId.idPattern);
    }

    EntityTags currentTags = entityTagsProvider.get(entityFlagsDescription.getId()).orElse(entityFlagsDescription);
    if (!currentTags.getTags().containsKey(UI_FLAGS_KEY) || !(currentTags.getTags().get(UI_FLAGS_KEY) instanceof Map)) {
      currentTags.getTags().put(UI_FLAGS_KEY, new HashMap<String, EntityFlag>());
    }
    synchronizeFlags(currentTags);

    getTask().updateStatus(
      BASE_PHASE,
      format("Updating flags on %s with %s", entityFlagsDescription.getId(), entityFlagsDescription.getFlags())
    );

    EntityTags durableEntityTags = front50Service.saveEntityTags(entityFlagsDescription);
    getTask().updateStatus(BASE_PHASE, format("Updated flags for %s in Front50", durableEntityTags.getId()));

    entityFlagsDescription.setLastModified(durableEntityTags.getLastModified());
    entityFlagsDescription.setLastModifiedBy(durableEntityTags.getLastModifiedBy());

    entityTagsProvider.index(entityFlagsDescription);
    entityTagsProvider.verifyIndex(entityFlagsDescription);

    getTask().updateStatus(BASE_PHASE, format("Indexed %s in ElasticSearch", entityFlagsDescription.getId()));
    return null;
  }

  private void synchronizeFlags(EntityTags currentTags) {
    String user = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");
    Long now = System.currentTimeMillis();

    Map<String, EntityFlag> currentFlags =
      (Map<String, EntityFlag>) currentTags.getTags().get(UI_FLAGS_KEY);

    entityFlagsDescription.getFlags().forEach((String key, EntityFlag flag) -> {
      if (flag.getCreatedBy() == null) {
        flag.setCreatedBy(user);
        flag.setCreated(now);
      }
      flag.setModifiedBy(user);
      flag.setModified(now);
      if (currentFlags.containsKey(key)) {
        flag.setCreatedBy(currentFlags.get(key).getCreatedBy());
        flag.setCreated(currentFlags.get(key).getCreated());
        flag.setStatus(currentFlags.get(key).getStatus());
      }
      currentFlags.put(key, flag);

    });
    entityFlagsDescription.setTags(currentTags.getTags());
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }
}
