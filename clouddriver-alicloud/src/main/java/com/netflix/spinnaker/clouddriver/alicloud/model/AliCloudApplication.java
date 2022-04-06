/*
 * Copyright 2022 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import com.netflix.spinnaker.clouddriver.model.Application;

public class AliCloudApplication implements Application, Serializable {
    private String name;
    /**
     * key: account
     * value: clusternames
     */
    private Map<String, Set<String>> clusterNames ;
    private Map<String, String> attributes ;

    public AliCloudApplication() {
    }

    public AliCloudApplication(String name, Map<String, Set<String>> clusterNames,
                               Map<String, String> attributes) {
        this.name = name;
        this.clusterNames = clusterNames;
        this.attributes = attributes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public Map<String, Set<String>> getClusterNames() {
        return clusterNames;
    }
}
