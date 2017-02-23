package com.netflix.spinnaker.clouddriver.dcos.deploy.util;

import com.google.common.base.Strings;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Specialized version of a {@link PathId} which has a specific structure based on Spinnaker concepts.
 * <p/>
 * The structure takes the form of {@code /<account>/<group>/<name>}, where account is a Spinnaker account, group is
 * basically the Spinnaker "region" concept (or namespace in kubernetes).
 */
public class DcosSpinnakerId {
    private final PathId marathonAppId;

    private DcosSpinnakerId(String account, String region, String serverGroupName) {
        checkArgument(!Strings.isNullOrEmpty(account), "account:null or empty");
        checkArgument(!Strings.isNullOrEmpty(region), "region:null or empty");
        checkArgument(!Strings.isNullOrEmpty(serverGroupName), "serverGroupName:null or empty");

        // The region may be supplied in the so-called "safe form" with underscores instead of backslashes which we use
        // throughout due to cache issues and deck issues when using backslashes as part of the region.
        this.marathonAppId = PathId.from(account).append(PathId.parse(region.replace("_", "/")))
                .append(serverGroupName);
    }

    private DcosSpinnakerId(PathId pathId) {
        this.marathonAppId = pathId;
    }

    /**
     * Creates a DcosSpinnakerId given an account, group, and name.
     * @param account the account (cannot be null or empty)
     * @param region the region (a.k.a account subgroup for dcos) (cannot be null or empty)
     * @param serverGroupName the name of the service (cannot be null or empty)
     * @return Non-null {@link DcosSpinnakerId} instance.
     */
    public static DcosSpinnakerId from(String account, String region, String serverGroupName) {
        return new DcosSpinnakerId(account, region, serverGroupName);
    }

    /**
     * Creates a DcosSpinnakerId given a fully qualified marathon application id. The Marathon ID must conform to
     * certain Spinnaker conventions, otherwise an {@link IllegalArgumentException} will be thrown. Call
     * {@link #validate(String, String)} first to ensure the id is parseable.
     * @return Possibly null {@link DcosSpinnakerId} instance if the application id doesn't have at least an account and
     *         name.
     * @throws IllegalArgumentException if the supplied marathonAppId is not {@link ##validate(String, String) valid}.
     */
    public static DcosSpinnakerId parse(String marathonAppId, String accountName) {

        PathId path = PathId.parse(marathonAppId);

        if (!validate(path, accountName)) {
            throw new IllegalArgumentException("Not a valid Spinnaker identifier!");
        }

        return new DcosSpinnakerId(path);
    }

    /**
     * Validates that a supplied marathon app id is structured such that it can be represented as a Spinnaker
     * id.
     * <p/>
     * The Marathon id must conform to the following conditions to be considered valid:
     * <ul>
     * <li>At least 3 parts - account, region/group, and server group name</li>
     * <li>The account part of the key must match the supplied accountName</li>
     * </ul>
     * @param marathonAppId The marathon app id to validate
     * @param accountName The account name against which the path should be validated (cannot be null or empty).
     * @return true if valid, false otherwise
     */
    public static boolean validate(String marathonAppId, final String accountName) {
        return validate(Strings.isNullOrEmpty(marathonAppId) ? PathId.from() : PathId.parse(marathonAppId),
                accountName);
    }

    /**
     * Validates that a supplied marathon {@link PathId} is structured such that it can be represented as a Spinnaker
     * id.
     * <p/>
     * The Marathon id must conform to the following conditions to be considered valid:
     * <ul>
     * <li>At least 3 parts - account, region/group, and server group name</li>
     * <li>The account part of the key must match the supplied accountName</li>
     * </ul>
     * @param marathonAppId The marathon {@link PathId} to validate
     * @param accountName The account name against which the path should be validated (cannot be null or empty).
     * @return true if valid, false otherwise
     */
    public static boolean validate(PathId marathonAppId, final String accountName) {
        checkArgument(!Strings.isNullOrEmpty(accountName), "accountName:null or empty");
        return marathonAppId != null &&
                marathonAppId.size() > 2 &&
                marathonAppId.first().get().equals(accountName);
    }

    public String getAccount() {
        return marathonAppId.first().orElseThrow(IllegalStateException::new);
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
        return marathonAppId.parent().tail().relative().toString();
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
        return getUnsafeRegion().replace("/", "_");
    }

    public String getName() {
        return marathonAppId.last().orElseThrow(IllegalStateException::new);
    }

    @Override
    public String toString() {
        return marathonAppId.toString();
    }

    public PathId toMarathonAppId() {
        return marathonAppId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DcosSpinnakerId other = (DcosSpinnakerId) o;
        return other.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
