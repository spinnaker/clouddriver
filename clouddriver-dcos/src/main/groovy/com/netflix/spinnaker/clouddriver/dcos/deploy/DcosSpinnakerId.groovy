package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.netflix.frigga.Names

class DcosSpinnakerId {
    private final static def REGION_SEPARATOR = "_"
    private final static def PATH_SEPARATOR = "/"
    final String account
    final String region
    final Names service

    public DcosSpinnakerId(final String marathonAppId) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(marathonAppId))

        List<String> parts = marathonAppId.split(PATH_SEPARATOR)

        if (marathonAppId.startsWith(PATH_SEPARATOR)) {
            Preconditions.checkArgument(parts.size() > 2)
            parts.remove(parts.first())
        } else {
            Preconditions.checkArgument(parts.size() > 1)
        }

        Preconditions.checkArgument(!Strings.nullToEmpty(parts.first()).trim().isEmpty(), "The account should not be null, empty, or blank.")
        Preconditions.checkArgument(!Strings.nullToEmpty(parts.last()).trim().isEmpty(), "The service should not be null, empty, or blank.")

        this.account = parts.first()
        this.service = Names.parseName(parts.last())

        def regionParts = parts.tail().take(parts.size() - 2).toList()

        if (regionParts.size() > 0) {
            def tempRegion = regionParts.join(REGION_SEPARATOR).split(REGION_SEPARATOR).toList()

            tempRegion.forEach({
                Preconditions.checkArgument(!it.trim().isEmpty(), "The region should not contain empty/blank parts")
            })

            this.region = tempRegion.join(PATH_SEPARATOR)
        } else {
            this.region = ""
        }
    }

    public DcosSpinnakerId(final String account, final String region, final String service) {
        Preconditions.checkArgument(!Strings.nullToEmpty(account).trim().isEmpty(), "The account should not be null, empty, or blank.")
        Preconditions.checkArgument(!Strings.nullToEmpty(service).trim().isEmpty(), "The service should not be null, empty, or blank.")

        this.account = account
        this.service = Names.parseName(service)
        this.region = Strings.nullToEmpty(region).replaceAll(REGION_SEPARATOR, PATH_SEPARATOR)

        if (this.region) {
            this.region.split(PATH_SEPARATOR).each { Preconditions.checkArgument(!it.trim().isEmpty(), "The region should not contain empty/blank parts") }
        }
    }

    public String getNamespace() {
        final def stringBuilder = new StringBuilder()

        stringBuilder.append(PATH_SEPARATOR).append(account)

        if (region) {
            stringBuilder.append(PATH_SEPARATOR).append(region)
        }

        return stringBuilder.toString()
    }

    public MarathonPath toMarathonPath() {
        return MarathonPath.parse(toString())
    }

    @Override
    public String toString() {
        return new StringBuilder().append(getNamespace()).append(PATH_SEPARATOR).append(service.getGroup())
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true
        if (o == null || getClass() != o.getClass())
            return false
        def dcosPathId = (DcosSpinnakerId) o
        return dcosPathId.toString() == toString()
    }

    @Override
    public int hashCode() {
        return toString().hashCode()
    }
}
