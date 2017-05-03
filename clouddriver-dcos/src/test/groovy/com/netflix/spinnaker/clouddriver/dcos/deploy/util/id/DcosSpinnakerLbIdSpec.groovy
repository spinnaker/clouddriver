package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import spock.lang.Specification

class DcosSpinnakerLbIdSpec extends Specification {
    static final def ACCOUNT = "spinnaker"
    static final def REGION = "us-west-1"
    static final def LOAD_BALANCER = "loadbalancer"
    static final def INVALID_ACCOUNT = "invalid-account"
    static final def INVALID_MARATHON_PART = "-iNv.aLiD-"

    void "static factory method should return an empty optional if the marathon app id is invalid"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.parseVerbose(appId, account, region)
            dcosPath == Optional.empty()

        where:
            appId | account | region
            "/" | ACCOUNT | REGION
            "" | ACCOUNT | REGION
            "/${ACCOUNT}" | ACCOUNT | REGION
            "${ACCOUNT}" | ACCOUNT | REGION
            "//${LOAD_BALANCER}" | ACCOUNT | REGION
            "/       /${LOAD_BALANCER}" | ACCOUNT | REGION
            "/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "/${ACCOUNT}/${INVALID_MARATHON_PART}" | ACCOUNT | REGION
            "${ACCOUNT}/${INVALID_MARATHON_PART}" | ACCOUNT | REGION
            "/${ACCOUNT}/" | ACCOUNT | REGION
            "${ACCOUNT}/" | ACCOUNT | REGION
            "/${ACCOUNT}/      " | ACCOUNT | REGION
            "${ACCOUNT}/      " | ACCOUNT | REGION
            "/${ACCOUNT}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "${ACCOUNT}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "/${ACCOUNT}//${LOAD_BALANCER}" | ACCOUNT | REGION
            "${ACCOUNT}//${LOAD_BALANCER}" | ACCOUNT | REGION
            "/${ACCOUNT}/      /${LOAD_BALANCER}" | ACCOUNT | REGION
            "${ACCOUNT}/      /${LOAD_BALANCER}" | ACCOUNT | REGION
            "/${ACCOUNT}/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "${ACCOUNT}/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "${INVALID_ACCOUNT}/${REGION}/${LOAD_BALANCER}" | ACCOUNT | REGION
            "${INVALID_ACCOUNT}/${REGION}/${LOAD_BALANCER}" | ACCOUNT | REGION
    }

    void "static factory method should return an empty optional if either account/loadBalancerName are invalid"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.fromVerbose(account, region, loadBalancerName)
            dcosPath == Optional.empty()

        where:
            account | region | loadBalancerName
            null | REGION | LOAD_BALANCER
            "" | REGION | LOAD_BALANCER
            "         " | REGION | LOAD_BALANCER
            INVALID_MARATHON_PART | REGION | LOAD_BALANCER
            ACCOUNT | null | LOAD_BALANCER
            ACCOUNT | "" | LOAD_BALANCER
            ACCOUNT | "         " | LOAD_BALANCER
            ACCOUNT | INVALID_MARATHON_PART | LOAD_BALANCER
            ACCOUNT | REGION | null
            ACCOUNT | REGION | ""
            ACCOUNT | REGION | "         "
            ACCOUNT | REGION | INVALID_MARATHON_PART
    }

    void "the account, and service should be correctly parsed when given a valid marathon path"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.parseVerbose(path, ACCOUNT, REGION).get()
            dcosPath.account == expectedAccount
            dcosPath.region == expectedRegion
            dcosPath.loadBalancerName == expectedLoadBalancerName
            dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

        where:
            path || expectedAccount || expectedRegion || expectedLoadBalancerName || expectedHaproxyGroup
            "${ACCOUNT}/${REGION}/${LOAD_BALANCER}" || ACCOUNT || REGION || LOAD_BALANCER || "${ACCOUNT}_${REGION}_${LOAD_BALANCER}".toString()
            "/${ACCOUNT}/${REGION}/${LOAD_BALANCER}" || ACCOUNT || REGION || LOAD_BALANCER || "${ACCOUNT}_${REGION}_${LOAD_BALANCER}".toString()
    }

    void "the account, and service should be correctly parsed when given a valid account and loadBalancerName"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.fromVerbose(ACCOUNT, REGION, LOAD_BALANCER).get()
            dcosPath.account == expectedAccount
            dcosPath.region == expectedRegion
            dcosPath.loadBalancerName == expectedLoadBalancerName
            dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

        where:
            expectedAccount || expectedRegion || expectedLoadBalancerName || expectedHaproxyGroup
            ACCOUNT || REGION || LOAD_BALANCER || "${ACCOUNT}_${REGION}_${LOAD_BALANCER}".toString()
            ACCOUNT || REGION || LOAD_BALANCER || "${ACCOUNT}_${REGION}_${LOAD_BALANCER}".toString()
    }
}
