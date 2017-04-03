package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import static com.google.common.base.Strings.nullToEmpty

import org.slf4j.LoggerFactory

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

    // TODO refactor all these static factory methods to not use a boolean param and instead separate them into
    // parseVerbose and fromVerbose functions to better describe the differences.
    public static Optional<DcosSpinnakerLbId> parse(final String id, final boolean log) {
        def marathonPath

        try {
            marathonPath = MarathonPathId.parse(id)
        } catch (IllegalArgumentException e) {
            logError(log, e.message)
            return Optional.empty()
        }

        if (marathonPath.size() != 2) {
            logError(log, "A DCOS Spinnaker LB ID should only contain 2 parts [${marathonPath.toString()}].")
            return Optional.empty()
        }

        def dcosSpinnakerLbId = new DcosSpinnakerLbId(marathonPath)

        Optional.of(dcosSpinnakerLbId)
    }

    public static Optional<DcosSpinnakerLbId> parse(final String id, final String account, final boolean log) {
        def dcosSpinnakerLbId = parse(id, log)

        if (!dcosSpinnakerLbId.isPresent()) {
            return Optional.empty()
        }

        if (dcosSpinnakerLbId.get().account != account) {
            logError(log, "The account [${account}] given does not match the account within the load balancer id [${dcosSpinnakerLbId.get().account}].")
            return Optional.empty()
        }

        dcosSpinnakerLbId
    }

    public static Optional<DcosSpinnakerLbId> from(final String account, final String loadBalancerName, final boolean log) {
        if (nullToEmpty(account).trim().empty) {
            logError(log, "The account should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (nullToEmpty(loadBalancerName).trim().empty) {
            logError(log, "The loadBalancerName should not be null, empty, or blank.")
            return Optional.empty()
        }

        if (account.contains(MarathonPathId.PART_SEPARATOR)) {
            logError(log, "The account [${account}] should not contain any '/' characters.")
            return Optional.empty()
        }
        if (loadBalancerName.contains(MarathonPathId.PART_SEPARATOR)) {
            logError(log, "The loadBalancerName [${loadBalancerName}] should not contain any '/' characters.")
            return Optional.empty()
        }

        def marathonPath

        try {
            marathonPath = MarathonPathId.parse("/${account}/${loadBalancerName}")
        } catch (IllegalArgumentException e) {
            logError(log, e.message)
            return Optional.empty()
        }

        Optional.of(new DcosSpinnakerLbId(marathonPath))
    }

    static void logError(boolean log, String message) {
        if (log) {
            LOGGER.error(message)
        }
    }
}
