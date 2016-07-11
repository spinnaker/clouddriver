/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.utils

import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

class DefaultRestTemplateFactorySpec extends Specification {

  void "should create a RestTemplate with buffering switched off"() {
    given:
    def factory = new DefaultRestTemplateFactory()

    when:
    def restTemplate = factory.createRestTemplate()

    then:
    restTemplate instanceof RestTemplate
    ReflectionTestUtils.getField(restTemplate.requestFactory, 'bufferRequestBody') == false
  }

}
