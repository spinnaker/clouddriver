package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.google.common.base.Preconditions

import static java.util.stream.Collectors.joining
/**
 * Represents a hierarchical Marathon appliation identifier.
 */
class MarathonPath {

    private final LinkedList<String> parts
    protected final boolean absolute

    public static MarathonPath from(String... parts) {
        return new MarathonPath(true, parts)
    }

    public static MarathonPath parse(String appId) {
        boolean absolute = appId.startsWith("/")
        final String[] parts = appId.split("/")
        if (absolute) {
            return new MarathonPath(true, Arrays.copyOfRange(parts, 1, parts.length))
        } else {
            return new MarathonPath(false, parts)
        }

    }

    protected MarathonPath(final boolean absolute, String... parts) {
        this(absolute, parts == null ? new LinkedList<String>() : new LinkedList<String>(Arrays.asList(parts)))
    }

    protected MarathonPath(final boolean absolute, MarathonPath other) {
        this(absolute, other.parts)
    }

    protected MarathonPath(final boolean absolute, final LinkedList<String> parts) {
        // TODO: could maybe split a part with "/" into multiple parts
        for (String part : parts) {
            Preconditions.checkArgument(!part.contains("/"))
        }
        this.parts = parts
        this.absolute = absolute
    }

    public MarathonPath relative() {
        return absolute ? new MarathonPath(false, this) : this
    }

    public String root() {
        return parts.headOption().orSome("")
    }

    public MarathonPath rootPath() {
        return new MarathonPath(absolute, parts.stream().findFirst().orElse(null))
    }

    public MarathonPath tail() {
        LinkedList<String> copy = new LinkedList<>(parts);
        copy.removeFirst();
        return new MarathonPath(absolute, copy);
    }

    public String[] parts() {
        return parts.toList().toJavaArray()
    }

    public String last() {
        return parts.last()
    }

    public MarathonPath parent() {
        if (parts.isEmpty()) {
            return this
        }

        LinkedList<String> copy = new LinkedList<>(parts);
        copy.removeLast();
        return new MarathonPath(absolute, copy)
    }

    public int length() {
        return parts.length()
    }

    public MarathonPath append(final MarathonPath path) {
        LinkedList<String> copy = new LinkedList<>(parts);
        copy.addAll(path.parts);
        return new MarathonPath(absolute, copy);
    }

    public MarathonPath append(final String part) {
        LinkedList<String> copy = new LinkedList<>(parts);
        copy.addLast(part);
        return new MarathonPath(absolute, copy);
    }

    public DcosSpinnakerId toSpinnakerId() {
        return new DcosSpinnakerId(toString())
    }

    @Override
    public String toString() {
        return toString(absolute, parts)
    }

    protected static String toString(boolean absolute, LinkedList<String> parts) {
        return parts.stream().collect(joining("/", absolute ? "/" : "", ""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true
        if (o == null || getClass() != o.getClass())
            return false
        MarathonPath pathId = (MarathonPath) o
        return pathId.toString() == toString()
    }

    @Override
    public int hashCode() {
        return toString().hashCode()
    }
}