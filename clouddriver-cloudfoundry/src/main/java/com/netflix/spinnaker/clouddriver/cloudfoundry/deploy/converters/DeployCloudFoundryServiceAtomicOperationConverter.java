/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.DEPLOY_SERVICE)
@Component
public class DeployCloudFoundryServiceAtomicOperationConverter
    extends AbstractCloudFoundryAtomicOperationConverter {
  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public DeployCloudFoundryServiceAtomicOperationConverter() {}

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFoundryServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployCloudFoundryServiceDescription convertDescription(Map input) {
    DeployCloudFoundryServiceDescription converted =
        getObjectMapper().convertValue(input, DeployCloudFoundryServiceDescription.class);
    converted.setClient(getClient(input));
    converted.setSpace(
        findSpace(converted.getRegion(), converted.getClient())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unable to find space '" + converted.getRegion() + "'.")));

    List<Map<Object, Object>> manifest = converted.getManifest();

    if (converted.isUserProvided()) {
      converted.setUserProvidedServiceAttributes(
          convertUserProvidedServiceManifest(manifest.stream().findFirst().orElse(null)));
    } else {
      converted.setServiceAttributes(convertManifest(manifest.stream().findFirst().orElse(null)));
    }
    return converted;
  }

  // visible for testing
  DeployCloudFoundryServiceDescription.ServiceAttributes convertManifest(Object manifestMap) {
    if (manifestMap == null) {
      throw new IllegalArgumentException("No configurations detected");
    }
    ServiceManifest manifest = objectMapper.convertValue(manifestMap, ServiceManifest.class);
    if (manifest.getService() == null) {
      throw new IllegalArgumentException("Manifest is missing the service");
    } else if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service instance name");
    } else if (manifest.getServicePlan() == null) {
      throw new IllegalArgumentException("Manifest is missing the service plan");
    }
    DeployCloudFoundryServiceDescription.ServiceAttributes attrs =
        new DeployCloudFoundryServiceDescription.ServiceAttributes();
    attrs.setService(manifest.getService());
    attrs.setServiceInstanceName(manifest.getServiceInstanceName());
    attrs.setServicePlan(manifest.getServicePlan());
    attrs.setTags(manifest.getTags());
    attrs.setUpdatable(manifest.isUpdatable());
    attrs.setParameterMap(manifest.getParameters());
    return attrs;
  }

  // visible for testing
  DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes
      convertUserProvidedServiceManifest(Object manifestMap) {
    if (manifestMap == null) {
      throw new IllegalArgumentException("No configurations detected");
    }
    UserProvidedServiceManifest manifest =
        objectMapper.convertValue(manifestMap, UserProvidedServiceManifest.class);
    if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service name");
    }
    DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes attrs =
        new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes();
    attrs.setServiceInstanceName(manifest.getServiceInstanceName());
    attrs.setSyslogDrainUrl(manifest.getSyslogDrainUrl());
    attrs.setRouteServiceUrl(manifest.getRouteServiceUrl());
    attrs.setTags(manifest.getTags());
    attrs.setUpdatable(manifest.isUpdatable());
    attrs.setCredentials(manifest.getCredentials());
    return attrs;
  }

  @Data
  private static class ServiceManifest {
    private String service;
    private boolean updatable = true;

    @JsonAlias({"service_instance_name", "serviceInstanceName"})
    private String serviceInstanceName;

    @JsonAlias({"service_plan", "servicePlan"})
    private String servicePlan;

    @Nullable private Set<String> tags;

    @Nullable
    @JsonDeserialize(using = OptionallySerializedMapDeserializer.class)
    private Map<String, Object> parameters;
  }

  @Data
  private static class UserProvidedServiceManifest {
    private boolean updatable = true;

    @JsonAlias({"service_instance_name", "serviceInstanceName"})
    private String serviceInstanceName;

    @Nullable
    @JsonAlias({"syslog_drain_url", "syslogDrainUrl"})
    private String syslogDrainUrl;

    @Nullable
    @JsonAlias({"route_service_url", "routeServiceUrl"})
    private String routeServiceUrl;

    @Nullable private Set<String> tags;

    @Nullable
    @JsonAlias({"credentials_map", "credentialsMap"})
    @JsonDeserialize(using = OptionallySerializedMapDeserializer.class)
    private Map<String, Object> credentials;
  }

  public static class OptionallySerializedMapDeserializer
      extends JsonDeserializer<Map<String, Object>> {

    private final TypeReference<Map<String, Object>> mapTypeReference =
        new TypeReference<Map<String, Object>>() {};

    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      JsonToken currentToken = parser.currentToken();

      Map<String, Object> deserializedMap = null;

      if (currentToken == JsonToken.START_OBJECT) {
        deserializedMap =
            context.readValue(parser, context.getTypeFactory().constructType(mapTypeReference));
      } else if (currentToken == JsonToken.VALUE_STRING) {
        String serizalizedMap = parser.getValueAsString();
        if (StringUtils.isNotBlank(serizalizedMap)) {
          deserializedMap =
              deserializeWithMappers(
                  serizalizedMap,
                  mapTypeReference,
                  yamlObjectMapper,
                  (ObjectMapper) parser.getCodec());
        }
      }

      return deserializedMap;
    }

    /**
     * Deserialize a String trying with multiple {@link ObjectMapper}.
     *
     * @return The value returned by the first mapper successfully deserializing the input.
     * @throws IOException When all ObjectMappers fail to deserialize the input.
     */
    private <T> T deserializeWithMappers(
        String serialized, TypeReference<T> typeReference, ObjectMapper... mappers)
        throws IOException {

      IOException deserializationFailed =
          new IOException("Could not deserialize value using the provided objectMappers");

      for (ObjectMapper mapper : mappers) {
        try {
          return mapper.readValue(serialized, typeReference);
        } catch (IOException e) {
          deserializationFailed.addSuppressed(e);
        }
      }
      throw deserializationFailed;
    }
  }
}
