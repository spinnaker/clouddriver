/*
 * Copyright 2021 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.Namespace;
import com.netflix.spinnaker.clouddriver.model.NamespaceProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/namespaces")
public class NamespaceController {

  private final List<NamespaceProvider> namespaceProviders;

  public NamespaceController(@Autowired(required = false) List<NamespaceProvider> providers) {
    this.namespaceProviders = providers != null ? providers : Collections.emptyList();
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{provider}/{account}", method = RequestMethod.GET)
  public Collection<Namespace> getNamespaces(
      @PathVariable String provider, @PathVariable String account) {
    NamespaceProvider namespaceProvider =
        namespaceProviders.stream()
            .filter(np -> np.getCloudProvider().equalsIgnoreCase(provider))
            .findAny()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "NamespaceProvider for provider " + provider + " not found."));
    return namespaceProvider.getNamespaces(account).stream()
        .sorted(Comparator.comparing(Namespace::getName))
        .collect(Collectors.toList());
  }
}
