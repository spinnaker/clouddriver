package com.netflix.spinnaker.clouddriver.dcos.deploy.validators.servergroup

import com.netflix.spinnaker.clouddriver.dcos.deploy.description.servergroup.AbstractDcosServerGroupDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.MarathonPathId

import org.springframework.validation.Errors

import com.netflix.spinnaker.clouddriver.dcos.deploy.validators.AbstractDcosDescriptionValidatorSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider

abstract class AbstractDcosServerGroupValidator<T extends AbstractDcosServerGroupDescription> extends AbstractDcosDescriptionValidatorSupport<T> {

    AbstractDcosServerGroupValidator(AccountCredentialsProvider accountCredentialsProvider, String descriptionName) {
        super(accountCredentialsProvider, descriptionName)
    }

    @Override
    void validate(List priorDescriptions, AbstractDcosServerGroupDescription description, Errors errors) {
        super.validate(priorDescriptions, description, errors)

        if (!description.region || description.region.empty) {
            errors.rejectValue "region", "${descriptionName}.region.empty"
        } else if (!isRegionValid(description.region)) {
            errors.rejectValue "region", "${descriptionName}.region.invalid"
        }

        if (!description.serverGroupName) {
            errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.empty"
        } else if (!MarathonPathId.isPartValid(description.serverGroupName)) {
            errors.rejectValue "serverGroupName", "${descriptionName}.serverGroupName.invalid"
        }
    }

    private static boolean isRegionValid(final String region) {
        def regionParts = region.replaceAll(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR)
                .split(MarathonPathId.PART_SEPARATOR).toList()

        for (part in regionParts) {
            if (!MarathonPathId.isPartValid(part)) {
                return false
            }
        }

        true
    }
}
