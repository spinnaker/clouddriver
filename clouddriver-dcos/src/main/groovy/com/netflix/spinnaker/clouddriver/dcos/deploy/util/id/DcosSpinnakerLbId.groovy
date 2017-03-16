package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import org.slf4j.LoggerFactory

import static com.google.common.base.Strings.nullToEmpty

/**
 * Represents a hierarchical Spinnaker specific load balancer identifier for DCOS.
 * Structure - /account/loadBalancerName
 */
class DcosSpinnakerLbId {
    private final static def LOGGER = LoggerFactory.getLogger(DcosSpinnakerLbId)
    public final static def SAFE_NAME_SEPARATOR = "_"

    private final def marathonPath

    public DcosSpinnakerLbId(final MarathonPathId marathonPath) {
        this.marathonPath = marathonPath
    }

    public String getAccount() {
        marathonPath.first().get()
    }

    public String getLoadBalancerName() {
        marathonPath.last().get()
    }

    public String getLoadBalancerHaproxyGroup() {
        marathonPath.relative().toString().replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_NAME_SEPARATOR)
    }

    @Override
    public String toString() {
        marathonPath.toString()
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true
        if (o == null || getClass() != o.getClass())
            return false
        def dcosPathId = (DcosSpinnakerLbId) o
        return dcosPathId.toString() == toString()
    }

    @Override
    public int hashCode() {
        return toString().hashCode()
    }

    public static Optional<DcosSpinnakerLbId> parse(final String id) {
        def marathonPath

        try {
            marathonPath = MarathonPathId.parse(id)
        } catch (IllegalArgumentException e) {
            LOGGER.error(e.message)
            return Optional.empty()
        }

        if (marathonPath.size() != 2) {
            LOGGER.error("A DCOS Spinnaker LB ID should only contain 2 parts.")
            return Optional.empty()
        }

        def dcosSpinnakerLbId = new DcosSpinnakerLbId(marathonPath)

        Optional.of(dcosSpinnakerLbId)
    }

    public static Optional<DcosSpinnakerLbId> parse(final String id, final String account) {
        def dcosSpinnakerLbId = parse(id)

        if (!dcosSpinnakerLbId.isPresent()) {
            return Optional.empty()
        }

        if (dcosSpinnakerLbId.get().account != account) {
            LOGGER.error("The account given does not match the account within the load balancer id.")
            return Optional.empty()
        }

        dcosSpinnakerLbId
    }

    public static Optional<DcosSpinnakerLbId> from(final String account, final String loadBalancerName) {
        if (nullToEmpty(account).trim().empty) {
            LOGGER.error("The account should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (nullToEmpty(loadBalancerName).trim().empty) {
            LOGGER.error("The loadBalancerName should not be null, empty, or blank.")
            return Optional.empty()
        }

        if (account.contains(MarathonPathId.PART_SEPARATOR)) {
            LOGGER.error("The account should not contain any '/' characters.")
            return Optional.empty()
        }
        if (loadBalancerName.contains(MarathonPathId.PART_SEPARATOR)) {
            LOGGER.error("The loadBalancerName should not contain any '/' characters.")
            return Optional.empty()
        }

        def marathonPath

        try {
            marathonPath = MarathonPathId.parse("/${account}/${loadBalancerName}")
        } catch (IllegalArgumentException e) {
            LOGGER.error(e.message)
            return Optional.empty()
        }

        Optional.of(new DcosSpinnakerLbId(marathonPath))
    }
}
