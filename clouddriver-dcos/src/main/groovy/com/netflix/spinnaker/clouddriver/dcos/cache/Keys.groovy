/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.cache

import com.netflix.frigga.Names

class Keys {
  public static final PROVIDER = "dcos"
  public static final DEFAULT_REGION = "default"

  static enum Namespace {
    IMAGES,
    SERVER_GROUPS,
    INSTANCES,
    LOAD_BALANCERS,
    CLUSTERS,
    APPLICATIONS,
    HEALTH,
    ON_DEMAND

    final String ns

    private Namespace() {
      def parts = name().split('_')
      ns = parts.tail().inject(new StringBuilder(parts.head().toLowerCase())) { val, next -> val.append(next.charAt(0)).append(next.substring(1).toLowerCase()) }
    }

    String toString() {
      ns
    }
  }

  static Map<String, String> parse(String key) {
    def parts = key.split(':')

    if (parts.length < 2) {
      return null
    }

    if (parts[0] != PROVIDER) {
      return null
    }

    def result = [provider: parts[0], type: parts[1]]

    switch (result.type) {
      case Namespace.IMAGES.ns:
        //TODO result << [account: parts[2], region: parts[3], imageId: parts[4]]
        break
      case Namespace.SERVER_GROUPS.ns:
        def names = Names.parseName(parts[5])
        //TODO result << [application: names.app.toLowerCase(), cluster: parts[2], account: parts[3], region: parts[4], serverGroup: parts[5], stack: names.stack, detail: names.detail, sequence: names.sequence?.toString()]
        break
      case Namespace.INSTANCES.ns:
        //TODO result << [id: parts[2]]
        break
      case Namespace.LOAD_BALANCERS.ns:
        def names = Names.parseName(parts[4])
        //TODO result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.CLUSTERS.ns:
        def names = Names.parseName(parts[4])
        //TODO result << [application: parts[2].toLowerCase(), account: parts[3], cluster: parts[4], stack: names.stack, detail: names.detail]
        break
      case Namespace.APPLICATIONS.ns:
        //TODO result << [application: parts[2].toLowerCase()]
        break
      case Namespace.HEALTH.ns:
        //TODO result << [id: parts[2], account: parts[3], region: parts[4], provider: parts[5]]
        break
      default:
        return null
        break
    }

    result
  }
}
