package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import com.google.common.base.Strings

import static com.google.common.base.Preconditions.checkArgument
import static com.google.common.base.Strings.nullToEmpty
import static java.util.stream.Collectors.joining

/**
 * Represents a hierarchical Marathon application identifier.
 */
class MarathonPathId {
    public final static String PART_PATTERN = $/[a-z0-9]+(-+[a-z0-9]+)*/$
    public final static String PART_SEPARATOR = "/"

    private final LinkedList<String> parts
    private final boolean absolute

    public static boolean validatePart(String part) {
        checkArgument(!nullToEmpty(part?.trim()).empty, "Marathon path parts cannot be empty or null")
        checkArgument(part.matches(PART_PATTERN), "Marathon path part [${part}] does not follow the correct naming pattern.")
    }

    public static MarathonPathId from(String... parts) {
        checkArgument(parts?.length > 0, "Marathon path cannot be empty or null")

        return new MarathonPathId(true, parts)
    }

    public static MarathonPathId parse(String appId) {
        checkArgument(!nullToEmpty(appId?.trim()).empty, "Marathon path cannot be empty or null")

        boolean absolute = appId.startsWith(PART_SEPARATOR)
        final String[] parts = appId.split(PART_SEPARATOR)
        if (absolute) {
            return new MarathonPathId(true, Arrays.copyOfRange(parts, 1, parts.length))
        } else {
            return new MarathonPathId(false, parts)
        }
    }

    private MarathonPathId(final boolean absolute, String... parts) {
        this.parts = new LinkedList<String>(Arrays.asList(parts))

        this.parts.forEach({part ->
            validatePart(part)
        })

        this.absolute = absolute
    }

    private MarathonPathId(final boolean absolute, MarathonPathId other) {
        this.parts = other.parts.clone() as LinkedList<String>
        this.absolute = absolute
    }

    public MarathonPathId absolute() {
        return absolute ? this : new MarathonPathId(true, this)
    }

    public MarathonPathId relative() {
        return absolute ? new MarathonPathId(false, this) : this
    }

    public String head() {
        parts.removeFirst()
    }

    public MarathonPathId tail() {
        if (parts.isEmpty()) {
            return this
        }

        LinkedList<String> copy = new LinkedList<>(parts)
        copy.removeFirst()
        return new MarathonPathId(absolute, copy.toArray() as String[])
    }

    public Optional<String> first() {
        return parts.isEmpty() ? Optional.empty() : Optional.of(parts.getFirst())
    }

    public Optional<String> last() {
        return parts.isEmpty() ? Optional.empty() : Optional.of(parts.getLast())
    }

    public MarathonPathId parent() {
        if (parts.isEmpty()) {
            return this
        }

        LinkedList<String> copy = new LinkedList<>(parts)
        copy.removeLast()
        return new MarathonPathId(absolute, copy.toArray() as String[])
    }

    public boolean isAbsolute() {
        absolute
    }

    public int size() {
        return parts.size()
    }

    public MarathonPathId append(final MarathonPathId path) {
        LinkedList<String> copy = new LinkedList<>(parts)
        copy.addAll(path.parts)
        return new MarathonPathId(absolute, copy.toArray() as String[])
    }

    public MarathonPathId append(final String part) {
        LinkedList<String> copy = new LinkedList<>(parts)
        copy.addLast(part)
        return new MarathonPathId(absolute, copy.toArray() as String[])
    }

    @Override
    public String toString() {
        return toString(absolute, parts)
    }

    protected static String toString(boolean absolute, LinkedList<String> parts) {
        return parts.stream().collect(joining(PART_SEPARATOR, absolute ? PART_SEPARATOR : "", ""))
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true
        if (o == null || getClass() != o.getClass())
            return false
        MarathonPathId pathId = (MarathonPathId) o
        return pathId.toString() == toString()
    }

    @Override
    public int hashCode() {
        return toString().hashCode()
    }
}