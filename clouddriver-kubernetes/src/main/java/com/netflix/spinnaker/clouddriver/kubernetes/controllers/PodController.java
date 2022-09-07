/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.controllers;

import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.KubernetesJobProvider;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.util.Collections;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{application}/kubernetes/pods")
public class PodController {
  private final KubernetesJobProvider kubernetesJobProvider;

  public PodController(KubernetesJobProvider jobProvider) {
    this.kubernetesJobProvider = jobProvider;
  }

  @PreAuthorize(
      "hasPermission(#application, 'APPLICATION', 'READ') and hasPermission(#account, 'ACCOUNT', 'READ')")
  @ApiOperation(value = "Collect a file from a pod", notes = "Collects the file result of a pod.")
  @RequestMapping(
      value = "/{account}/{namespace}/{podName}/{fileName:.+}",
      method = RequestMethod.GET)
  Map<String, Object> getFileContents(
      @ApiParam(value = "Application name", required = true) @PathVariable String application,
      @ApiParam(value = "Account job was created by", required = true) @PathVariable String account,
      @ApiParam(value = "Namespace in which the pod is running in", required = true) @PathVariable
          String namespace,
      @ApiParam(value = "Unique identifier of pod being looked up", required = true) @PathVariable
          String podName,
      @ApiParam(value = "File name to look up", required = true) @PathVariable String fileName) {
    Map<String, Object> results =
        kubernetesJobProvider.getFileContentsFromPod(account, namespace, podName, fileName);

    if (results != null) {
      return results;
    }

    return Collections.emptyMap();
  }
}
