/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.dcos.deploy.description.securitygroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.DcosAtomicOperationDescription
import groovy.transform.AutoClone
import groovy.transform.Canonical

@AutoClone
@Canonical
class DcosSecurityGroupDescription extends DcosAtomicOperationDescription {
  String securityGroupName
  String app
  String stack
  String detail
  String namespace

  DcosIngressBackend ingress
  List<DcosIngressTls> tls
  List<DcosIngressRule> rules
}

@AutoClone
@Canonical
class DcosIngressBackend {
  String serviceName
  int port
}

@AutoClone
@Canonical
class DcosIngressTls {
  List<String> hosts
  String secretName
}

@AutoClone
@Canonical
class DcosIngressRule {
  String host
  DcosIngressRuleValue value
}

@AutoClone
@Canonical
class DcosIngressRuleValue {
  DcosHttpIngressRuleValue http
}

@AutoClone
@Canonical
class DcosHttpIngressRuleValue {
  List<DcosHttpIngressPath> paths
}

@AutoClone
@Canonical
class DcosHttpIngressPath {
  String path
  DcosIngressBackend ingress
}
