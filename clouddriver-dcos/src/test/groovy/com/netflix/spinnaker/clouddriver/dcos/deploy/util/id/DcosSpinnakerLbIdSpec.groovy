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
            def dcosPath = DcosSpinnakerLbId.parseVerbose(appId, account)
            dcosPath == Optional.empty()

        where:
            appId | account
            "/" | ACCOUNT
            "" | ACCOUNT
            "/${ACCOUNT}" | ACCOUNT
            "${ACCOUNT}" | ACCOUNT
            "//${LOAD_BALANCER}" | ACCOUNT
            "/       /${LOAD_BALANCER}" | ACCOUNT
            "/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT
            "${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT
            "/${ACCOUNT}/${INVALID_MARATHON_PART}" | ACCOUNT
            "${ACCOUNT}/${INVALID_MARATHON_PART}" | ACCOUNT
            "/${ACCOUNT}/" | ACCOUNT
            "${ACCOUNT}/" | ACCOUNT
            "/${ACCOUNT}/      " | ACCOUNT
            "${ACCOUNT}/      " | ACCOUNT
            "/${ACCOUNT}//${LOAD_BALANCER}" | ACCOUNT
            "${ACCOUNT}//${LOAD_BALANCER}" | ACCOUNT
            "/${ACCOUNT}/      /${LOAD_BALANCER}" | ACCOUNT
            "${ACCOUNT}/      /${LOAD_BALANCER}" | ACCOUNT
            "/${ACCOUNT}/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT
            "${ACCOUNT}/${INVALID_MARATHON_PART}/${LOAD_BALANCER}" | ACCOUNT
            "${INVALID_ACCOUNT}/${REGION}/${LOAD_BALANCER}" | ACCOUNT
            "${INVALID_ACCOUNT}/${REGION}/${LOAD_BALANCER}" | ACCOUNT
    }

    void "static factory method should return an empty optional if either account/loadBalancerName are invalid"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.fromVerbose(account, loadBalancerName)
            dcosPath == Optional.empty()

        where:
            account | loadBalancerName
            null | LOAD_BALANCER
            "" | LOAD_BALANCER
            "         " | LOAD_BALANCER
            INVALID_MARATHON_PART | LOAD_BALANCER
            ACCOUNT | null
            ACCOUNT | ""
            ACCOUNT | "         "
            ACCOUNT | INVALID_MARATHON_PART
    }

    void "the account, and service should be correctly parsed when given a valid marathon path"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.parseVerbose(path, ACCOUNT).get()
            dcosPath.account == expectedAccount
            dcosPath.loadBalancerName == expectedLoadBalancerName
            dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

        where:
            path || expectedAccount || expectedLoadBalancerName || expectedHaproxyGroup
            "${ACCOUNT}/${LOAD_BALANCER}" || ACCOUNT || LOAD_BALANCER || "${ACCOUNT}_${LOAD_BALANCER}".toString()
            "/${ACCOUNT}/${LOAD_BALANCER}" || ACCOUNT || LOAD_BALANCER || "${ACCOUNT}_${LOAD_BALANCER}".toString()
    }

    void "the account, and service should be correctly parsed when given a valid account and loadBalancerName"() {
        expect:
            def dcosPath = DcosSpinnakerLbId.fromVerbose(ACCOUNT, LOAD_BALANCER).get()
            dcosPath.account == expectedAccount
            dcosPath.loadBalancerName == expectedLoadBalancerName
            dcosPath.loadBalancerHaproxyGroup == expectedHaproxyGroup

        where:
            expectedAccount || expectedLoadBalancerName || expectedHaproxyGroup
            ACCOUNT || LOAD_BALANCER || "${ACCOUNT}_${LOAD_BALANCER}".toString()
            ACCOUNT || LOAD_BALANCER || "${ACCOUNT}_${LOAD_BALANCER}".toString()
    }
}
