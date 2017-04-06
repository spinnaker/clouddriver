/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.netflix.spinnaker.clouddriver.docker.registry.security.DockerRegistryNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.api.KubernetesApiAdaptor;
import com.netflix.spinnaker.clouddriver.kubernetes.config.LinkedDockerRegistryConfiguration;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolationException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.stream.Collectors;

public class KubernetesCredentials {
  private final KubernetesApiAdaptor apiAdaptor;
  private final List<String> namespaces;
  private final List<String> omitNamespaces;
  private final List<LinkedDockerRegistryConfiguration> dockerRegistries;
  private final HashMap<String, Set<String>> imagePullSecrets;
  private final Logger LOG;
  private final AccountCredentialsRepository repository;
  private final HashSet<String> dynamicRegistries;
  private List<String> oldNamespaces;

  // TODO(lwander): refactor apiAdaptor into KubernetesNamedAccountCredentials, and any other metadata that isn't
  // strictly a credential.
  public KubernetesCredentials(KubernetesApiAdaptor apiAdaptor,
                               List<String> namespaces,
                               List<String> omitNamespaces,
                               List<LinkedDockerRegistryConfiguration> dockerRegistries,
                               AccountCredentialsRepository accountCredentialsRepository) {
    this.apiAdaptor = apiAdaptor;
    this.namespaces = namespaces != null ? namespaces : new ArrayList<>();
    this.omitNamespaces = omitNamespaces != null ? omitNamespaces : new ArrayList<>();
    this.oldNamespaces = this.namespaces;
    this.dynamicRegistries = new HashSet<>();
    this.dockerRegistries = dockerRegistries != null ? dockerRegistries : new ArrayList<>();
    for (LinkedDockerRegistryConfiguration config : this.dockerRegistries) {
      if (config.getNamespaces() == null || config.getNamespaces().isEmpty()) {
        dynamicRegistries.add(config.getAccountName());
      }
    }
    this.imagePullSecrets = new HashMap<>();
    this.repository = accountCredentialsRepository;
    this.LOG = LoggerFactory.getLogger(KubernetesCredentials.class);

    List<String> knownNamespaces = !this.namespaces.isEmpty() ? this.namespaces : apiAdaptor.getNamespacesByName();
    reconfigureRegistries(knownNamespaces, knownNamespaces);
  }

  public List<String> getNamespaces() {
    if (namespaces != null && !namespaces.isEmpty()) {
      // If namespaces are provided, used them
      return namespaces;
    } else {
      List<String> addedNamespaces = apiAdaptor.getNamespacesByName();
      addedNamespaces.removeAll(omitNamespaces);

      List<String> resultNamespaces = new ArrayList<>(addedNamespaces);

      // Find the namespaces that were added, and add docker secrets to them. No need to track deleted
      // namespaces since they delete their secrets automatically.
      addedNamespaces.removeAll(oldNamespaces);
      reconfigureRegistries(addedNamespaces, resultNamespaces);
      oldNamespaces = resultNamespaces;

      return resultNamespaces;
    }
  }

  private void reconfigureRegistries(List<String> affectedNamespaces, List<String> allNamespaces) {
    for (int i = 0; i < dockerRegistries.size(); i++) {
      LinkedDockerRegistryConfiguration registry = dockerRegistries.get(i);
      List<String> registryNamespaces = registry.getNamespaces();
      // If a registry was not initially configured with any namespace, it can deploy to any namespace, otherwise
      // restrict the deploy to the registryNamespaces
      if (!dynamicRegistries.contains(registry.getAccountName())) {
        affectedNamespaces = registryNamespaces;
      } else {
        registry.setNamespaces(allNamespaces);
      }

      if (affectedNamespaces != null && !affectedNamespaces.isEmpty()) {
        LOG.info("Adding secrets for docker registry " + registry.getAccountName() + " in " + affectedNamespaces);
      }

      DockerRegistryNamedAccountCredentials account = (DockerRegistryNamedAccountCredentials) repository.getOne(registry.getAccountName());

      if (account == null) {
        throw new IllegalArgumentException("The account " + registry.getAccountName() + " was not configured inside Clouddriver.");
      }

      for (String namespace : affectedNamespaces) {
        Namespace res = apiAdaptor.getNamespace(namespace);
        if (res == null) {
          NamespaceBuilder namespaceBuilder = new NamespaceBuilder();
          Namespace newNamespace = namespaceBuilder.withNewMetadata().withName(namespace).endMetadata().build();
          apiAdaptor.createNamespace(newNamespace);
        }

        SecretBuilder secretBuilder = new SecretBuilder();
        String secretName = registry.getAccountName();

        secretBuilder = secretBuilder.withNewMetadata().withName(secretName).withNamespace(namespace).endMetadata();

        HashMap<String, String> secretData = new HashMap<>(1);
        String dockerCfg = String.format("{ \"%s\": { \"auth\": \"%s\", \"email\": \"%s\" } }",
                                         account.getAddress(),
                                         account.getBasicAuth(),
                                         account.getEmail());

        try {
          dockerCfg = new String(Base64.getEncoder().encode(dockerCfg.getBytes("UTF-8")), "UTF-8");
        } catch (UnsupportedEncodingException uee) {
          throw new IllegalStateException("Unable to encode docker config ", uee);
        }
        secretData.put(".dockercfg", dockerCfg);

        secretBuilder = secretBuilder.withData(secretData).withType("kubernetes.io/dockercfg");
        try {
          Secret newSecret = secretBuilder.build();
          Secret oldSecret = apiAdaptor.getSecret(namespace, secretName);
          if (oldSecret != null) {
            if (oldSecret.getData().equals(newSecret.getData())) {
              LOG.info("Skipping creation of duplicate secret " + secretName + " in namespace " + namespace);
            } else {
              apiAdaptor.editSecret(namespace, secretName).addToData(newSecret.getData()).done();
            }
          } else {
            apiAdaptor.createSecret(namespace, secretBuilder.build());
          }
        } catch (ConstraintViolationException cve) {
          throw new IllegalStateException("Unable to build secret: " + cve.getMessage() +
                                          " due to violations " + cve.getConstraintViolations(),
                                          cve);
        }

        Set<String> existingSecrets = imagePullSecrets.get(namespace);
        existingSecrets = existingSecrets != null ? existingSecrets : new HashSet<>();
        existingSecrets.add(secretName);
        imagePullSecrets.put(namespace, existingSecrets);
      }
    }
  }

  public KubernetesApiAdaptor getApiAdaptor() {
    return apiAdaptor;
  }

  public List<LinkedDockerRegistryConfiguration> getDockerRegistries() {
    return dockerRegistries;
  }

  public Map<String, Set<String>> getImagePullSecrets() {
    return imagePullSecrets;
  }

  public Boolean isRegisteredNamespace(String namespace) {
    return getNamespaces().contains(namespace);
  }

  public Boolean isRegisteredImagePullSecret(String secret, String namespace) {
    Set<String> secrets = imagePullSecrets.get(namespace);
    if (secrets == null) {
      return false;
    }
    return secrets.contains(secret);
  }
}
