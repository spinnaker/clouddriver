package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import com.netflix.frigga.Names
import spock.lang.Specification

class DcosSpinnakerAppIdSpec extends Specification {
    static final def ACCOUNT = "spinnaker"
    static final def INVALID_MARATHON_PART = "-iNv.aLiD-"

    void "static factory method should return an empty optional if the given path was invalid"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.parseVerbose(path)
            dcosPath.present == present

        where:
            path || present
            null || Boolean.FALSE
            "" || Boolean.FALSE
            "      " || Boolean.FALSE
            "${INVALID_MARATHON_PART}" || Boolean.FALSE
            "/" || Boolean.FALSE
            "/       " || Boolean.FALSE
            "/${INVALID_MARATHON_PART}" || Boolean.FALSE
            "spinnaker" || Boolean.FALSE
            "spinnaker/" || Boolean.FALSE
            "spinnaker/         " || Boolean.FALSE
            "spinnaker/${INVALID_MARATHON_PART}" || Boolean.FALSE
            "/spinnaker" || Boolean.FALSE
            "/spinnaker/" || Boolean.FALSE
            "/spinnaker/         " || Boolean.FALSE
            "/spinnaker/${INVALID_MARATHON_PART}" || Boolean.FALSE
            "spinnaker/service-v000" || Boolean.FALSE
            "spinnaker//service-v000" || Boolean.FALSE
            "spinnaker/         /service-v000" || Boolean.FALSE
            "spinnaker/${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
            "spinnaker/test//service-v000" || Boolean.FALSE
            "spinnaker/test/         /service-v000" || Boolean.FALSE
            "spinnaker/test/${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
            "spinnaker/test_/service-v000" || Boolean.FALSE
            "spinnaker/test_         /service-v000" || Boolean.FALSE
            "spinnaker/test_${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
            "/spinnaker/service-v000" || Boolean.FALSE
            "/spinnaker//service-v000" || Boolean.FALSE
            "/spinnaker/         /service-v000" || Boolean.FALSE
            "/spinnaker/${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
            "/spinnaker/test//service-v000" || Boolean.FALSE
            "/spinnaker/test/         /service-v000" || Boolean.FALSE
            "/spinnaker/test/${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
            "/spinnaker/test_/service-v000" || Boolean.FALSE
            "/spinnaker/test_         /service-v000" || Boolean.FALSE
            "/spinnaker/test_${INVALID_MARATHON_PART}/service-v000" || Boolean.FALSE
    }

    void "static factory method should return an empty optional if the given account, region, and/or serverGroupName was invalid"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.fromVerbose(account, region, serverGroupName)
            dcosPath.present == present

        where:
            account | region | serverGroupName || present
            null | "test/service" | "service-v000" || Boolean.FALSE
            "" | "test/service" | "service-v000" || Boolean.FALSE
            "     " | "test/service" | "service-v000" || Boolean.FALSE
            "${INVALID_MARATHON_PART}" | "test/service" | "service-v000" || Boolean.FALSE
            ACCOUNT | null | "service-v000" || Boolean.FALSE
            ACCOUNT | "" | "service-v000" || Boolean.FALSE
            ACCOUNT | "    " | "service-v000" || Boolean.FALSE
            ACCOUNT | "${INVALID_MARATHON_PART}" | "service-v000" || Boolean.FALSE
            ACCOUNT | "test/service" | null || Boolean.FALSE
            ACCOUNT | "test/service" | "" || Boolean.FALSE
            ACCOUNT | "test/service" | "         " || Boolean.FALSE
            ACCOUNT | "test/service" | "${INVALID_MARATHON_PART}" || Boolean.FALSE
    }

    void "the account, region, and service should be correctly parsed when given a valid marathon path"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
            dcosPath.account == expectedAccount
            dcosPath.dcosCluster == expectedDcosCluster
            dcosPath.unsafeRegion == expectedUnsafeRegion
            dcosPath.safeRegion == expectedSafeRegion
            dcosPath.safeGroup == expectedSafeGroup
            dcosPath.unsafeGroup == expectedUnsafeGroup
            dcosPath.serverGroupName == Names.parseName(expectedService)

        where:
            path || expectedAccount || expectedDcosCluster || expectedUnsafeRegion || expectedSafeRegion || expectedSafeGroup || expectedUnsafeGroup || expectedService
            "spinnaker/test/service-v000" || "spinnaker" || "test" || "test" || "test" || "" || "" || "service-v000"
            "spinnaker/test/service/service-v000" || "spinnaker" || "test" || "test/service" || "test_service" || "service" || "service" || "service-v000"
    }

    void "the account, region, and service should be correctly parsed when given a valid marathon absolute path"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
            dcosPath.account == expectedAccount
            dcosPath.dcosCluster == expectedDcosCluster
            dcosPath.unsafeRegion == expectedUnsafeRegion
            dcosPath.safeRegion == expectedSafeRegion
            dcosPath.safeGroup == expectedSafeGroup
            dcosPath.unsafeGroup == expectedUnsafeGroup
            dcosPath.serverGroupName == Names.parseName(expectedService)

        where:
            path || expectedAccount || expectedDcosCluster || expectedUnsafeRegion || expectedSafeRegion || expectedSafeGroup || expectedUnsafeGroup || expectedService
            "/spinnaker/test/service-v000" || "spinnaker" || "test" || "test" || "test" || "" || "" || "service-v000"
            "/spinnaker/test/service/service-v000" || "spinnaker" || "test" || "test/service" || "test_service" || "service" || "service" || "service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid account, region, service"() {
        expect:
        def dcosPath = DcosSpinnakerAppId.fromVerbose(account, region, serverGroupName).get()
        dcosPath.toString() == expectedFullPath

        where:
        account | region | serverGroupName || expectedNamespace || expectedFullPath
        "spinnaker" | "test" | "service-v000" || "/spinnaker/test" || "/spinnaker/test/service-v000"
        "spinnaker" | "test/service" | "service-v000" || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
        "spinnaker" | "test_service" | "service-v000" || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid marathon path"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
            dcosPath.toString() == expectedFullPath

        where:
            path || expectedFullPath
            "spinnaker/test/service-v000" || "/spinnaker/test/service-v000"
            "spinnaker/test/service/service-v000" || "/spinnaker/test/service/service-v000"
    }

    void "the namespace and full path should be correctly built when given a valid absolute marathon path"() {
        expect:
            def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
            dcosPath.toString() == expectedFullPath

        where:
            path || expectedFullPath
            "/spinnaker/test/service-v000" || "/spinnaker/test/service-v000"
            "/spinnaker/test/service/service-v000" || "/spinnaker/test/service/service-v000"
    }
}
