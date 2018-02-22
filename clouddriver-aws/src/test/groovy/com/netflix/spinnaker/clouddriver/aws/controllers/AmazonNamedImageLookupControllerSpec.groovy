/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.clouddriver.aws.controllers

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.aws.controllers.AmazonNamedImageLookupController.LookupOptions
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import spock.lang.Specification
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest

class AmazonNamedImageLookupControllerSpec extends Specification {

  void "should extract tags from query parameters"() {
    given:
    def httpServletRequest = httpServletRequest(["tag:tag1": "value1", "tag:Tag2": "value2"])

    expect:
    AmazonNamedImageLookupController.extractTagFilters(httpServletRequest) == ["tag1": "value1", "tag2": "value2"]
  }

  void "should support filtering on 1..* tags"() {
    given:
    def namedImage1 = new AmazonNamedImageLookupController.NamedImage(
      amis: ["us-east-1": ["ami-123"]],
      tagsByImageId: ["ami-123": ["state": "released"]]
    )
    def namedImage2 = new AmazonNamedImageLookupController.NamedImage(
      amis: ["us-east-1": ["ami-456"]],
      tagsByImageId: ["ami-456": ["state": "released", "engine": "spinnaker"]]
    )

    and:
    def controller = new AmazonNamedImageLookupController(null)

    expect:
    // single tag ... matches all
    controller.filter([namedImage1, namedImage2], ["state": "released"]) == [namedImage1, namedImage2]

    // multiple tags ... matches one (case-insensitive)
    controller.filter([namedImage1, namedImage2], ["STATE": "released", "engine": "SpinnakeR"]) == [namedImage2]

    // single tag ... matches none
    controller.filter([namedImage1, namedImage2], ["xxx": "released"]) == []

    // no tags ... matches all
    controller.filter([namedImage1, namedImage2], [:]) == [namedImage1, namedImage2]
  }

  @Unroll
  void "should prevent searches on bad ami-like query: #query"() {
    given:
    def controller = new AmazonNamedImageLookupController(null)

    when:
    controller.validateLookupOptions(new LookupOptions(q: query))

    then:
    thrown(InvalidRequestException)

    where:
    query << ["ami", "ami-", "ami-1234", "ami-123456789"]
  }

  @Unroll
  void "should not throw exception when performing acceptable ami-like query: #query"() {
    given:
    def controller = new AmazonNamedImageLookupController(Stub(Cache))

    when:
    controller.validateLookupOptions(new LookupOptions(q: query))

    then:
    notThrown(InvalidRequestException)

    where:
    query << ["ami_", "ami-12345678", "sami", "ami_12345678", "ami-1234567890abcdef0"]
  }

  private HttpServletRequest httpServletRequest(Map<String, String> tagFilters) {
    return Mock(HttpServletRequest) {
      getParameterNames() >> {
        new Vector(["param1"] + tagFilters.keySet()).elements()
      }
      getParameter(_) >> { String key -> tagFilters.get(key) }
    }
  }
}
