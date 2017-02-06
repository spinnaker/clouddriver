package com.netflix.spinnaker.clouddriver.dcos.deploy.util;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

/**
 * Represents a hierarchical Marathon appliation identifier.
 */
public class PathId {

    private final boolean absolute;
    protected final LinkedList<String> parts;

    public static PathId from(String... parts) {
        return new PathId(true, parts);
    }

    public static PathId parse(String appId) {
        boolean absolute = appId.startsWith("/");
        final String[] parts = appId.split("/");
        if (absolute) {
            return new PathId(true, Arrays.copyOfRange(parts, 1, parts.length));
        } else {
            return new PathId(false, parts);
        }
    }

    private PathId(final boolean absolute, LinkedList<String> parts) {
        parts.forEach(part -> Preconditions.checkArgument(!part.contains("/")));

        this.parts = parts;
        this.absolute = absolute;
    }

    private PathId(final boolean absolute, String... parts) {
        this(absolute, parts == null ? new LinkedList<String>() : new LinkedList<String>(Arrays.asList(parts)));
    }

    private PathId(final boolean absolute, PathId other) {
        this(absolute, other.parts);
    }

    public PathId relative() {
        return absolute ? new PathId(false, this) : this;
    }

    public Optional<String> first() {
        return parts.isEmpty() ? Optional.empty() : Optional.of(parts.getFirst());
    }

    public PathId tail() {

        if (parts.isEmpty()) {
            return this;
        }

        LinkedList<String> copy = new LinkedList<>(parts);
        copy.removeFirst();
        return new PathId(absolute, copy);
    }

    public Optional<String> last() {
        return parts.isEmpty() ? Optional.empty() : Optional.of(parts.getLast());
    }

    public PathId parent() {
        if (parts.isEmpty()) {
            return this;
        }

        LinkedList<String> copy = new LinkedList<>(parts);
        copy.removeLast();
        return new PathId(absolute, copy);
    }

    public int size() {
        return parts.size();
    }

    public PathId append(final PathId path) {
        LinkedList<String> copy = new LinkedList<>(parts);
        copy.addAll(path.parts);
        return new PathId(absolute, copy);
    }

    public PathId append(final String part) {
        LinkedList<String> copy = new LinkedList<>(parts);
        copy.addLast(part);
        return new PathId(absolute, copy);
    }

    @Override
    public String toString() {
        return toString(absolute, parts);
    }

    protected static String toString(boolean absolute, LinkedList<String> parts) {
        return parts.stream().collect(joining("/", absolute ? "/" : "", ""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PathId pathId = (PathId) o;
        return pathId.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}