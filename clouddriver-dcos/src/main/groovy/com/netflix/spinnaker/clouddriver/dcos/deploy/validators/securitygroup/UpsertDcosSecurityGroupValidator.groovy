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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.securitygroup

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.securitygroup.DcosHttpIngressPath
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.securitygroup.DcosIngressRule
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.securitygroup.DcosSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.StandardDcosAttributeValidator
import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.validation.Errors

class UpsertDcosSecurityGroupValidator {
  class UpsertDcosLoadBalancerAtomicOperationValidator extends DescriptionValidator<DcosSecurityGroupDescription> {
    @Autowired
    AccountCredentialsProvider accountCredentialsProvider

    @Override
    void validate(List priorDescriptions, DcosSecurityGroupDescription description, Errors errors) {
      def helper = new StandardDcosAttributeValidator("upsertDcosSecurityGroupDescription", errors)

      if (!helper.validateCredentials(description.account, accountCredentialsProvider)) {
        return
      }

      DcosCredentials credentials = (DcosCredentials) accountCredentialsProvider.getCredentials(description.account).credentials

      helper.validateName(description.securityGroupName, "securityGroupName")
      helper.validateNamespace(credentials, description.namespace, "namespace")

      if (description.ingress) {
        if (description.ingress.serviceName) {
          helper.validateName(description.ingress.serviceName, "ingress.serviceName")
        }
        if (description.ingress.port) {
          helper.validatePort(description.ingress.port, "ingress.port")
        }
      }

      if (description.rules) {
        description.rules.eachWithIndex { DcosIngressRule rule, i ->
          if (rule.host) {
            helper.validateName(rule.host, "rules[$i].host")
          }
          rule.value?.http?.paths?.eachWithIndex{ DcosHttpIngressPath path, j ->
            if (path.path) {
              helper.validatePath(path.path, "rules[$i].value.http.paths[$j].path")
            }
          }
        }
      }
    }
  }
}
