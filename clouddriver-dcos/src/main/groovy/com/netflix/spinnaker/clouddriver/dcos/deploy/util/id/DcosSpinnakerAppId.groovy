package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import static com.google.common.base.Preconditions.checkArgument
import static com.google.common.base.Strings.nullToEmpty

import com.netflix.frigga.Names

/**
 * Represents a hierarchical Spinnaker specific application identifier for DCOS.
 * Structure - /account/region/app-stack-detail-sequence
 */
class DcosSpinnakerAppId {
    public final static def SAFE_REGION_SEPARATOR = "_"

    private final def marathonPath

    public DcosSpinnakerAppId(final String marathonAppId) {
        this.marathonPath = MarathonPathId.parse(nullToEmpty(marathonAppId)).absolute()

        checkArgument(this.marathonPath.size() > 2, "A part of the DCOS Spinnaker App ID was missing.")

        validateServerGroupName(this.marathonPath.last().get())
    }

    public DcosSpinnakerAppId(final String marathonAppId, final String accountName) {
        this(marathonAppId)

        checkArgument(this.account == accountName, "Account expected in DCOS Spinnaker App ID does not match given account name.")
    }

    public DcosSpinnakerAppId(final String account, final String region, final String serverGroupName) {
        checkArgument(!nullToEmpty(account).trim().empty, "The account should not be null, empty, or blank.")
        checkArgument(!nullToEmpty(region).trim().empty, "The region should not be null, empty, or blank.")
        checkArgument(!nullToEmpty(serverGroupName).trim().empty, "The serverGroupName should not be null, empty, or blank.")

        validateServerGroupName(serverGroupName)

        marathonPath = MarathonPathId.parse("/${account}/${region.replaceAll(SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR)}/${serverGroupName}")
    }

    public String getAccount() {
        marathonPath.first().get()
    }

    /**
     * @return The canonical DC/OS "region" (a.k.a the full group path in which the marathon application lives)
     *         including backslashes. This is returned as a relative path, meaning no preceeding backslash. Will never
     *         be null.
     *         <p/>
     *         Deemed unsafe because various Spinnaker components have trouble with a region with backslashes.
     *         <p/>
     *         Ex: {@code foo/bar}
     * @see #getSafeRegion()
     */
    public String getUnsafeRegion() {
        marathonPath.tail().parent().relative().toString()
    }

    /**
     * @return The "safe" DC/OS region (a.k.a the group in which the marathon application lives). This is returned as a
     *         relative path, meaning no preceeding underscore. Will never be null.
     *         <p/>
     *         Deemed safe because backslashes are replaced with underscores.
     *         <p/>
     *         Ex: {@code acct_foo_bar}
     * @see #getSafeRegion()
     */
    public String getSafeRegion() {
        region.replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_REGION_SEPARATOR)
    }

    public Names getServerGroupName() {
        Names.parseName(marathonPath.last().get())
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
        def dcosPathId = (DcosSpinnakerAppId) o
        return dcosPathId.toString() == toString()
    }

    @Override
    public int hashCode() {
        return toString().hashCode()
    }

    private static void validateServerGroupName(final String serverGroupName) {
        def service = Names.parseName(serverGroupName)

        checkArgument(!nullToEmpty(service.app).trim().empty, "The server group app should not be null, empty, or blank.")
        checkArgument(service.sequence >= 0, "The server group sequence should not be negative or null.")
    }

    public static Optional<DcosSpinnakerAppId> from(String marathonAppId) {
        try {
            Optional.of(new DcosSpinnakerAppId(marathonAppId))
        } catch (IllegalArgumentException e) {
            Optional.empty()
        }
    }

    public static Optional<DcosSpinnakerAppId> from(String marathonAppId, final String accountName) {
        try {
            Optional.of(new DcosSpinnakerAppId(marathonAppId, accountName))
        } catch (IllegalArgumentException e) {
            Optional.empty()
        }
    }

    public static Optional<DcosSpinnakerAppId> from(final String account, final String region, final String service) {
        try {
            Optional.of(new DcosSpinnakerAppId(account, region, service))
        } catch (IllegalArgumentException e) {
            Optional.empty()
        }
    }
}
