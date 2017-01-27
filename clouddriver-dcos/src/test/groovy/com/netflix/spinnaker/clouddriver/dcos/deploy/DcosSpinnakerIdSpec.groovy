package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.frigga.Names
import spock.lang.Specification

class DcosSpinnakerIdSpec extends Specification {
    void "constructor should throw an IllegalArgumentException if account is null"() {
        setup:
        def account = null
        def region = "test/service"
        def service = "service-v000"

        when:
        new DcosSpinnakerId(account, region, service)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if account is an empty string"() {
        setup:
            def account = ""
            def region = "test/service"
            def service = "service-v000"

        when:
            new DcosSpinnakerId(account, region, service)

        then:
            thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if account is an blank string"() {
        setup:
        def account = "     "
        def region = "test/service"
        def service = "service-v000"

        when:
        new DcosSpinnakerId(account, region, service)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if service is null"() {
        setup:
        def account = "spinnaker"
        def region = "test/service"
        def service = null

        when:
        new DcosSpinnakerId(account, region, service)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if service is an empty string"() {
        setup:
            def account = "spinnaker"
            def region = "test/service"
            def service = ""

        when:
            new DcosSpinnakerId(account, region, service)

        then:
            thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if service is an blank string"() {
        setup:
        def account = "spinnaker"
        def region = "test/service"
        def service = "         "

        when:
        new DcosSpinnakerId(account, region, service)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path is null"() {
        setup:
        def path = null

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path is an empty string"() {
        setup:
        def path = ""

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path is a blank string"() {
        setup:
        def path = "      "

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path is an empty string ignoring the root/absolute path"() {
        setup:
        def path = "/"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path is a blank string ignoring the root/absolute path"() {
        setup:
        def path = "/       "

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has no service specified"() {
        setup:
        def path = "spinnaker"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has an empty service specified"() {
        setup:
        def path = "spinnaker/"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank service specified"() {
        setup:
        def path = "spinnaker/         "

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has no service specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has an empty service specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker/"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank service specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker/         "

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has an empty region specified"() {
        setup:
        def path = "spinnaker//service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank region specified"() {
        setup:
        def path = "spinnaker/         /service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank part in the region specified"() {
        setup:
        def path = "spinnaker/test/         /service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has an empty region specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker//service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank region specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker/         /service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if path has a blank part in the region specified ignoring the root/absolute path"() {
        setup:
        def path = "/spinnaker/test/         /service-v000"

        when:
        new DcosSpinnakerId(path)

        then:
        thrown IllegalArgumentException
    }

    void "the account, region, and service should be correctly parsed when given a valid marathon path"() {
        expect:
            def dcosPath = new DcosSpinnakerId(path)
            dcosPath.account == expectedAccount
            dcosPath.region == expectedRegion
            dcosPath.service == Names.parseName(expectedService)

        where:
            path || expectedAccount || expectedRegion || expectedService
            "spinnaker/service-v000" || "spinnaker" || "" || "service-v000"
            "spinnaker/test/service-v000" || "spinnaker" || "test" || "service-v000"
            "spinnaker/test/service/service-v000" || "spinnaker" || "test/service" || "service-v000"
    }

    void "the account, region, and service should be correctly parsed when given a valid marathon absolute path"() {
        expect:
            def dcosPath = new DcosSpinnakerId(path)
            dcosPath.account == expectedAccount
            dcosPath.region == expectedRegion
            dcosPath.service == Names.parseName(expectedService)

        where:
            path || expectedAccount || expectedRegion || expectedService
            "/spinnaker/service-v000" || "spinnaker" || "" || "service-v000"
            "/spinnaker/test/service-v000" || "spinnaker" || "test" || "service-v000"
            "/spinnaker/test/service/service-v000" || "spinnaker" || "test/service" || "service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid account, region, service"() {
        expect:
        def dcosPath = new DcosSpinnakerId(account, region, service)
        dcosPath.namespace == expectedNamespace
        dcosPath.toString() == expectedFullPath

        where:
        account | region | service || expectedNamespace || expectedFullPath
        "spinnaker" | null | "service-v000" || "/spinnaker" || "/spinnaker/service-v000"
        "spinnaker" | "" | "service-v000" || "/spinnaker" || "/spinnaker/service-v000"
        "spinnaker" | "test" | "service-v000" || "/spinnaker/test" || "/spinnaker/test/service-v000"
        "spinnaker" | "test/service" | "service-v000" || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid marathon path"() {
        expect:
            def dcosPath = new DcosSpinnakerId(path)
            dcosPath.namespace == expectedNamespace
            dcosPath.toString() == expectedFullPath

        where:
            path || expectedNamespace || expectedFullPath
            "spinnaker/service-v000" || "/spinnaker" || "/spinnaker/service-v000"
            "spinnaker/test/service-v000" || "/spinnaker/test" || "/spinnaker/test/service-v000"
            "spinnaker/test/service/service-v000" || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid absolute marathon path"() {
        expect:
            def dcosPath = new DcosSpinnakerId(path)
            dcosPath.namespace == expectedNamespace
            dcosPath.toString() == expectedFullPath

        where:
            path || expectedNamespace || expectedFullPath
            "/spinnaker/service-v000" || "/spinnaker" || "/spinnaker/service-v000"
            "/spinnaker/test/service-v000" || "/spinnaker/test" || "/spinnaker/test/service-v000"
            "/spinnaker/test/service/service-v000" || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
    }
}
