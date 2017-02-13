package com.netflix.spinnaker.clouddriver.dcos.deploy.util;

/**
 * Specialized version of a {@link PathId} which has a specific structure based on Spinnaker concepts.
 * <p/>
 * The structure takes the form of {@code /<account>/<group>/<name>}, where account is a Spinnaker account, group is
 * basically the Spinnaker "region" concept (or namespace in kubernetes).
 */
public class DcosSpinnakerId {
    private final PathId marathonAppId;

    private DcosSpinnakerId(String account, String region, String appName) {
        this.marathonAppId = createPathId(account, region, appName);
    }

    private DcosSpinnakerId(PathId pathId) {
        this.marathonAppId = pathId;
    }

    /**
     * Creates a DcosSpinnakerId given an account and name.
     * @param account the account (cannot be null)
     * @param name the name of the service (cannot be null)
     * @return Non-null {@link DcosSpinnakerId} instance.
     */
    public static DcosSpinnakerId from(String account, String name) {
        return new DcosSpinnakerId(account, null, name);
    }

    /**
     * Creates a DcosSpinnakerId given an account, group, and name.
     * @param account the account (cannot be null)
     * @param region the region (a.k.a account subgroup for dcos) (may be null)
     * @param name the name of the service (cannot be null)
     * @return Non-null {@link DcosSpinnakerId} instance.
     */
    public static DcosSpinnakerId from(String account, String region, String name) {
        return new DcosSpinnakerId(account, region, name);
    }

    public static DcosSpinnakerId from(PathId pathId) {
        return new DcosSpinnakerId(pathId);
    }

    public static boolean validate(String marathonAppId, final String accountName) {
        PathId path = PathId.parse(marathonAppId);
        return path.first().isPresent() && path.first().get().equals(accountName);
    }

    /**
     * Creates a DcosSpinnakerId given a fully qualified marathon application id. The Marathon ID must conform to
     * certain Spinnaker conventions, otherwise an {@link IllegalArgumentException} will be thrown. Call
     * {@link #validate(String, String)} first to ensure the id is parsable.
     * @return Possibly null {@link DcosSpinnakerId} instance if the application id doesn't have at least an account and
     *         name.
     * @throws IllegalArgumentException if the supplied marathonAppId is not {@link ##validate(String, String) valid}.
     */
    public static DcosSpinnakerId parse(String marathonAppId, String accountName) {
        if (!validate(marathonAppId, accountName)) {
            throw new IllegalArgumentException();
        }

        return new DcosSpinnakerId(PathId.parse(marathonAppId));
    }

    private static PathId createPathId(String account, String region, String appName) {
        // The region may be in the so-called "safe form" with underscores instead of backslashes which we use
        // throughout due to cache issues and deck issues when using backslashes as part of the region.
        PathId parsedRegion = (region == null || region.isEmpty()) ? PathId.from()
                : PathId.parse(region.replace("_", "/"));

        // If we're parsing a region supplied by the UI, the region will contain the account in it.
        if (parsedRegion.first().isPresent() && parsedRegion.first().get().equals(account)) {
            parsedRegion = parsedRegion.tail();
        }

        return PathId.from(account).append(parsedRegion).append(appName);
    }

    public String getAccount() {
        return marathonAppId.first().orElseThrow(IllegalStateException::new);
    }

    /**
     * @return The canonical DC/OS "region" (a.k.a the full group path in which the marathon application lives)
     *         including backslashes. This is returned as a relative path, meaning no preceeding backslash. Also note
     *         that this includes the "account", or root node, in the path. Will never be null.
     *         <p/>
     *         Deemed unsafe because various Spinnaker components have trouble with a region with backslashes.
     *         <p/>
     *         Ex: {@code acct/foo/bar/}
     * @see #getSafeRegion()
     */
    public String getUnsafeRegion() {
        return marathonAppId.parent().relative().toString();
    }

    /**
     * @return The "safe" DC/OS region (a.k.a the group in which the marathon application lives). This is returned as a
     *         relative path, meaning no preceeding underscore. Note that this includes the "account", or root node, in
     *         the path. Will never be null.
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
