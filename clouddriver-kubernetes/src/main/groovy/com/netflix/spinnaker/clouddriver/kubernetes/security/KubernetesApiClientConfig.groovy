/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.security

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.SSLUtils;

import groovy.util.logging.Slf4j
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import javax.net.ssl.KeyManager;

@Slf4j
public class KubernetesApiClientConfig extends Config {
  String kubeconfigFile
  String context
  String cluster
  String user

  public KubernetesApiClientConfig(String kubeconfigFile, String context, String cluster, String user) {
    this.kubeconfigFile = kubeconfigFile
    this.context = context
    this.user = user
  }

  public ApiClient getApiCient() throws Exception {
    InputStream is = new FileInputStream(kubeconfigFile);
    Reader input = new InputStreamReader(is);
    Yaml yaml = new Yaml(new SafeConstructor());
    Object config = yaml.load(input);
    Map<String, Object> configMap = (Map<String, Object>)config;

    //Override these values
    if (this.context) {
      configMap.put("current-context", this.context)
    }

    if (this.cluster) {
      configMap.put("clusters", cluster)
    }

    if (this.user) {
      configMap.put("users", user)
    }

    yaml = new Yaml(new SafeConstructor());
    String out = yaml.dump(configMap);
    InputStream kubeconfigIS = new ByteArrayInputStream(out.getBytes());

    return (kubeconfigFile ? fromConfig(kubeconfigIS) : Config.defaultClient())
  }
}
