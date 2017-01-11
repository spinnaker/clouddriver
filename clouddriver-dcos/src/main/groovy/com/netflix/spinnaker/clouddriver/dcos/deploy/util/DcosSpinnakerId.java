package com.netflix.spinnaker.clouddriver.dcos.deploy.util;

/**
 * Specialized version of a {@link PathId} which has a specific structure based on Spinnaker concepts.
 * <p/>
 * The structure takes the form of {@code /<account>/<group>/<name>}, where account is a Spinnaker account, group is
 * basically the Spinnaker "region" concept (or namespace in kubernetes).
 */
public class DcosSpinnakerId {
    private final PathId marathonAppId;

    private DcosSpinnakerId(String account, String group, String appName) {
        this.marathonAppId = createPathId(account, group, appName);
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
     * @param group the group (may be null)
     * @param name the name of the service (cannot be null)
     * @return Non-null {@link DcosSpinnakerId} instance.
     */
    public static DcosSpinnakerId from(String account, String group, String name) {
        return new DcosSpinnakerId(account, group, name);
    }

    private static PathId createPathId(String account, String group, String appName) {
        PathId parsedGroup = group == null ? PathId.from() : PathId.parse(group);
        return PathId.from(account).append(parsedGroup).append(appName);
    }

    public String getAccount() {
        return marathonAppId.root();
    }

    public String getGroup() {
        // TODO Should this be relative?
        return marathonAppId.parent().tail().toString();
    }

    public String getName() {
        return marathonAppId.last();
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
