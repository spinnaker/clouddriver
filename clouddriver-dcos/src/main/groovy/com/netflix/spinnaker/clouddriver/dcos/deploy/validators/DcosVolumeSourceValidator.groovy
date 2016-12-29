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

package com.netflix.spinnaker.clouddriver.dcos.deploy.validators

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DcosVolumeSource
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.DcosVolumeSourceType

class DcosVolumeSourceValidator {
  static void validate(DcosVolumeSource source, StandardDcosAttributeValidator helper, String prefix) {
    helper.validateName(source.name, "${prefix}.name")
    switch (source.type) {
      case DcosVolumeSourceType.EmptyDir:
        helper.validateNotEmpty(source.emptyDir, "${prefix}.emptyDir")

        break // Nothing else to validate, only property is an enum which is implicitly validated during deserialization

      case DcosVolumeSourceType.HostPath:
        if (!helper.validateNotEmpty(source.hostPath, "${prefix}.hostPath")) {
          break
        }
        helper.validatePath(source.hostPath.path, "${prefix}.hostPath.path")

        break

      case DcosVolumeSourceType.PersistentVolumeClaim:
        if (!helper.validateNotEmpty(source.persistentVolumeClaim, "${prefix}.persistentVolumeClaim")) {
          break
        }
        helper.validateName(source.persistentVolumeClaim.claimName, "${prefix}.persistentVolumeClaim.claimName")

        break

      case DcosVolumeSourceType.Secret:
        if (!helper.validateNotEmpty(source.secret, "${prefix}.secret")) {
          break
        }
        helper.validateName(source.secret.secretName, "${prefix}.secret.secretName")

        break

      default:
        helper.reject("${prefix}.type", "$source.type not supported")
    }
  }
}
