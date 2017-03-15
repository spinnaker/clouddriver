package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import static com.google.common.base.Preconditions.checkArgument
import static com.google.common.base.Strings.nullToEmpty

/**
 * Represents a hierarchical Spinnaker specific load balancer identifier for DCOS.
 * Structure - /account/loadBalancerName
 */
class DcosSpinnakerLbId {
    public final static def SAFE_NAME_SEPARATOR = "_"

    private final def marathonPath

    public DcosSpinnakerLbId(final String id) {
        this.marathonPath = MarathonPathId.parse(nullToEmpty(id)).absolute()

        checkArgument(this.marathonPath.size() == 2, "A part of the DCOS Spinnaker LB ID was missing.")
    }

    public DcosSpinnakerLbId(final String account, final String loadBalancerName) {
        checkArgument(!nullToEmpty(account).trim().empty, "The account should not be null, empty, or blank.")
        checkArgument(!nullToEmpty(loadBalancerName).trim().empty, "The loadBalancerName should not be null, empty, or blank.")

        marathonPath = MarathonPathId.parse("/${account}/${loadBalancerName}")
    }

    public String getAccount() {
        marathonPath.first().get()
    }

    public String getLoadBalancerName() {
        marathonPath.last().get()
    }

    public String getUnsafeLoadBalancerGroup() {
        marathonPath.relative().toString()
    }

    public String getSafeLoadBalancerGroup() {
        unsafeLoadBalancerGroup.replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_NAME_SEPARATOR)
    }

    public MarathonPathId toMarathonPathId() {
        marathonPath
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

    public static Optional<DcosSpinnakerLbId> from(final String id) {
        try {
            Optional.of(new DcosSpinnakerLbId(id))
        } catch (IllegalArgumentException e) {
            Optional.empty()
        }
    }

    public static Optional<DcosSpinnakerLbId> from(final String account, final String loadBalancerName) {
        try {
            Optional.of(new DcosSpinnakerLbId(account, loadBalancerName))
        } catch (IllegalArgumentException e) {
            Optional.empty()
        }
    }
}
