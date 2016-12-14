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

package com.netflix.spinnaker.clouddriver.elasticsearch.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.UpsertEntityFlagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.UpsertEntityFlagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("upsertEntityFlags")
public class UpsertEntityFlagsAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  private final ObjectMapper objectMapper;
  private final Front50Service front50Service;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;

  @Autowired
  public UpsertEntityFlagsAtomicOperationConverter(ObjectMapper objectMapper,
                                                  Front50Service front50Service,
                                                  ElasticSearchEntityTagsProvider entityTagsProvider) {
    this.objectMapper = objectMapper
      .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
  }

  public AtomicOperation convertOperation(Map input) {
    return new UpsertEntityFlagsAtomicOperation(
      front50Service, entityTagsProvider, this.convertDescription(input)
    );
  }

  public UpsertEntityFlagsDescription convertDescription(Map input) {
    return objectMapper.convertValue(input, UpsertEntityFlagsDescription.class);
  }
}
