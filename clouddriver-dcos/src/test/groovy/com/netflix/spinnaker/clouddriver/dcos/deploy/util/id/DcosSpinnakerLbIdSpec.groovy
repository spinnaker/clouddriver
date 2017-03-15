package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import spock.lang.Specification

class DcosSpinnakerLbIdSpec extends Specification {
    static final def ACCOUNT = "spinnaker"
    static final def LOAD_BALANCER = "loadbalancer"
    
    void "constructor should throw an IllegalArgumentException if account is null"() {
        setup:
        def account = null
        def loadBalancer = LOAD_BALANCER

        when:
        new DcosSpinnakerLbId(account, loadBalancer)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if account is an empty string"() {
        setup:
            def account = ""
            def loadBalancer = LOAD_BALANCER

        when:
            new DcosSpinnakerLbId(account, loadBalancer)

        then:
            thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if account is an blank string"() {
        setup:
        def account = "     "
        def loadBalancer = LOAD_BALANCER

        when:
        new DcosSpinnakerLbId(account, loadBalancer)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if loadBalancer is null"() {
        setup:
        def account = ACCOUNT
        def loadBalancer = null

        when:
        new DcosSpinnakerLbId(account, loadBalancer)

        then:
        thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if loadBalancer is an empty string"() {
        setup:
            def account = ACCOUNT
            def loadBalancer = ""

        when:
            new DcosSpinnakerLbId(account, loadBalancer)

        then:
            thrown IllegalArgumentException
    }

    void "constructor should throw an IllegalArgumentException if loadBalancer is an blank string"() {
        setup:
        def account = ACCOUNT
        def loadBalancer = "         "

        when:
        new DcosSpinnakerLbId(account, loadBalancer)

        then:
        thrown IllegalArgumentException
    }

    void "the account, and service should be correctly parsed when given a valid marathon path"() {
        expect:
            def dcosPath = new DcosSpinnakerLbId(ACCOUNT, LOAD_BALANCER)
            dcosPath.account == expectedAccount
            dcosPath.unsafeLoadBalancerGroup == expectedUnsafeGroup
            dcosPath.safeLoadBalancerGroup == expectedSafeGroup
            dcosPath.loadBalancerName == expectedLoadBalancerName

        where:
            path || expectedAccount || expectedLoadBalancerName || expectedUnsafeGroup || expectedSafeGroup
            "${ACCOUNT}/${LOAD_BALANCER}" || ACCOUNT || LOAD_BALANCER || "${ACCOUNT}/${LOAD_BALANCER}" || "${ACCOUNT}_${LOAD_BALANCER}"
    }

    void "the account, and service should be correctly parsed when given a valid marathon absolute path"() {
        expect:
            def dcosPath = new DcosSpinnakerLbId(ACCOUNT, LOAD_BALANCER)
            dcosPath.account == expectedAccount
            dcosPath.unsafeLoadBalancerGroup == expectedUnsafeGroup
            dcosPath.safeLoadBalancerGroup == expectedSafeGroup
            dcosPath.loadBalancerName == expectedLoadBalancerName

        where:
        path || expectedAccount || expectedLoadBalancerName || expectedUnsafeGroup || expectedSafeGroup
        "/${ACCOUNT}/${LOAD_BALANCER}" || ACCOUNT || LOAD_BALANCER || "${ACCOUNT}/${LOAD_BALANCER}" || "${ACCOUNT}_${LOAD_BALANCER}"
    }
}
