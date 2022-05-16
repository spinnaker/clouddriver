/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.artifacts.ivy.settings;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import org.apache.ivy.plugins.resolver.DependencyResolver;

@Data
abstract class Resolver<M extends DependencyResolver> {
  /** The name which identifies the resolver. */
  @JacksonXmlProperty(isAttribute = true)
  private String name;

  public abstract M toIvyModel();

  protected M toIvyModel(M partial) {
    partial.setName(name);
    return partial;
  }
}
