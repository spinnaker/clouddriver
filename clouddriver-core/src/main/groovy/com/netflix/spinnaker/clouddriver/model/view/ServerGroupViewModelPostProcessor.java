/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.model.view;

import com.netflix.spinnaker.clouddriver.model.ServerGroup;

/**
 * (Optionally) used in clouddriver-web by the ServerGroupController to mutate server group API data.
 */
public interface ServerGroupViewModelPostProcessor<T extends ServerGroup> {

  boolean supports(ServerGroup serverGroup);

  void process(T serverGroup);
}
