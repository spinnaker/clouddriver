package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import static com.google.common.base.Strings.nullToEmpty

import org.slf4j.LoggerFactory

import com.netflix.frigga.Names

/**
 * Represents a hierarchical Spinnaker specific application identifier for DCOS.
 * Structure - /account/region/app-stack-detail-sequence
 */
class DcosSpinnakerAppId {
    private final static def LOGGER = LoggerFactory.getLogger(DcosSpinnakerAppId)
    public final static def SAFE_REGION_SEPARATOR = "_"

    private final def marathonPath

    private DcosSpinnakerAppId(final MarathonPathId marathonPath) {
        this.marathonPath = marathonPath
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
        unsafeRegion.replaceAll(MarathonPathId.PART_SEPARATOR, SAFE_REGION_SEPARATOR)
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

    // TODO refactor all these static factory methods to not use a boolean param and instead separate them into
    // parseVerbose and fromVerbose functions to better describe the differences.
    public static Optional<DcosSpinnakerAppId> parse(String marathonAppId, boolean log) {
        def marathonPath

        try {
            marathonPath = MarathonPathId.parse(nullToEmpty(marathonAppId)).absolute()
        } catch (IllegalArgumentException e) {
            logError(log, e.message)
            return Optional.empty()
        }

        if (marathonPath.size() < 3) {
            logError(log, "A part of the DCOS Spinnaker App ID was missing [${marathonPath.toString()}].")
            return Optional.empty()
        }

        def service = Names.parseName(marathonPath.last().get())

        if (nullToEmpty(service.app).trim().empty) {
            logError(log, "The server group app should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (service.sequence < 0) {
            logError(log, "The server group sequence should not be negative or null.")
            return Optional.empty()
        }

        Optional.of(new DcosSpinnakerAppId(marathonPath))
    }

    public static Optional<DcosSpinnakerAppId> parse(String marathonAppId, final String account, boolean log) {
        def dcosSpinnakerAppId = parse(marathonAppId, log)

        if (!dcosSpinnakerAppId.isPresent()) {
            return Optional.empty()
        }

        if (dcosSpinnakerAppId.get().account != account) {
            logError(log, "The account [${account}] given does not match the account within the app id [${dcosSpinnakerAppId.get().account}].")
            return Optional.empty()
        }

        dcosSpinnakerAppId
    }

    public static Optional<DcosSpinnakerAppId> from(final String account, final String region, final String serverGroupName, boolean log) {
        if (nullToEmpty(account).trim().empty) {
            logError(log, "The account should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (nullToEmpty(region).trim().empty) {
            logError(log, "The region should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (nullToEmpty(serverGroupName).trim().empty) {
            logError(log, "The serverGroupName should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (account.contains(MarathonPathId.PART_SEPARATOR)) {
            logError(log, "The account [${account}] should not contain any '/' characters.")
            return Optional.empty()
        }
        if (serverGroupName.contains(MarathonPathId.PART_SEPARATOR)) {
            logError(log, "The serverGroupName [${serverGroupName}] should not contain any '/' characters.")
            return Optional.empty()
        }

        def marathonPath

        try {
            marathonPath = MarathonPathId.parse("/${account}/${region.replaceAll(SAFE_REGION_SEPARATOR, MarathonPathId.PART_SEPARATOR)}/${serverGroupName}")
        } catch (IllegalArgumentException e) {
            logError(log, e.message)
            return Optional.empty()
        }

        def service = Names.parseName(serverGroupName)

        if (nullToEmpty(service.app).trim().empty) {
            logError(log, "The server group app should not be null, empty, or blank.")
            return Optional.empty()
        }
        if (service.sequence < 0) {
            logError(log, "The server group sequence should not be negative or null.")
            return Optional.empty()
        }

        Optional.of(new DcosSpinnakerAppId(marathonPath))
    }

    static void logError(boolean log, String message) {
        if (log) {
            LOGGER.error(message)
        }
    }
}
