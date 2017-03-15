package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import spock.lang.Specification

class MarathonPathIdSpec extends Specification {
    static final def ACCOUNT = "spinnaker"

    void "constructor should throw an IllegalArgumentException if path is invalid"() {
        expect:
        Optional<MarathonPathId> marathonPath = { -> try {
            return Optional.of(MarathonPathId.parse(path))
        } catch (IllegalArgumentException e) {
            return Optional.empty()
        }}.call()

        marathonPath.isPresent() == expectedToBePresent

        where:
        path || expectedToBePresent
        null || Boolean.FALSE
        "" || Boolean.FALSE
        "/" || Boolean.FALSE
        "    " || Boolean.FALSE
        "/    " || Boolean.FALSE
        "." || Boolean.FALSE
        "/." || Boolean.FALSE
        ".." || Boolean.FALSE
        "/.." || Boolean.FALSE
        "-" || Boolean.FALSE
        "/-" || Boolean.FALSE
        "--" || Boolean.FALSE
        "/--" || Boolean.FALSE
        ".app" || Boolean.FALSE
        "/.app" || Boolean.FALSE
        "-app" || Boolean.FALSE
        "/-app" || Boolean.FALSE
        "app." || Boolean.FALSE
        "/app." || Boolean.FALSE
        "app-" || Boolean.FALSE
        "/app-" || Boolean.FALSE
        "ap.p" || Boolean.FALSE
        "/ap.p" || Boolean.FALSE
        "ap-p" || Boolean.TRUE
        "/ap-p" || Boolean.TRUE
        "ap..p" || Boolean.FALSE
        "/ap..p" || Boolean.FALSE
        "ap--p" || Boolean.TRUE
        "/ap--p" || Boolean.TRUE
        "app" || Boolean.TRUE
        "/app" || Boolean.TRUE
    }

    void "constructor should throw an IllegalArgumentException if parts are invalid"() {
        expect:
        Optional<MarathonPathId> marathonPath = { -> try {
            return Optional.of(MarathonPathId.from(parts))
        } catch (IllegalArgumentException e) {
            return Optional.empty()
        }}.call()

        marathonPath.isPresent() == expectedToBePresent

        where:
        parts || expectedToBePresent
        null || Boolean.FALSE
        ["", "app"] as String[] || Boolean.FALSE
        ["/", "app"] as String[] || Boolean.FALSE
        ["   ", "app"] as String[] || Boolean.FALSE
        ["/   ", "app"] as String[] || Boolean.FALSE
        [".", "app"] as String[] || Boolean.FALSE
        ["/.", "app"] as String[] || Boolean.FALSE
        ["..", "app"] as String[] || Boolean.FALSE
        ["/..", "app"] as String[] || Boolean.FALSE
        ["-", "app"] as String[] || Boolean.FALSE
        ["/-", "app"] as String[] || Boolean.FALSE
        ["--", "app"] as String[] || Boolean.FALSE
        ["/--", "app"] as String[] || Boolean.FALSE
        [".app", "app"] as String[] || Boolean.FALSE
        ["/.app", "app"] as String[] || Boolean.FALSE
        ["-app", "app"] as String[] || Boolean.FALSE
        ["/-app", "app"] as String[] || Boolean.FALSE
        ["app.", "app"] as String[] || Boolean.FALSE
        ["/app.", "app"] as String[] || Boolean.FALSE
        ["app-", "app"] as String[] || Boolean.FALSE
        ["/app-", "app"] as String[] || Boolean.FALSE
        ["ap.p", "app"] as String[] || Boolean.FALSE
        ["/ap.p", "app"] as String[] || Boolean.FALSE
        ["ap-p", "app"] as String[] || Boolean.TRUE
        ["/ap-p", "app"] as String[] || Boolean.FALSE
        ["ap..p", "app"] as String[] || Boolean.FALSE
        ["/ap..p", "app"] as String[] || Boolean.FALSE
        ["ap--p", "app"] as String[] || Boolean.TRUE
        ["/ap--p", "app"] as String[] || Boolean.FALSE
        ["app", "app"] as String[] || Boolean.TRUE
        ["/app", "app"] as String[] || Boolean.FALSE
    }
}
