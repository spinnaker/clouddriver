/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers


import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import spock.lang.Specification

class OperationsControllerSpec extends Specification {
  def descriptionAuthorizer = Mock(DescriptionAuthorizer)



  @Configuration
  static class TestConfig {
    @Bean
    Converter1 desc1() {
      new Converter1()
    }

    @Bean
    Converter2 desc2() {
      new Converter2()
    }
  }

  static class Op1 implements AtomicOperation {
    Object operate(List priorOutputs) {
      return null
    }
  }

  static class Op2 implements AtomicOperation {
    Object operate(List priorOutputs) {
      return null
    }
  }

  static class Converter1 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op1()
    }

    Object convertDescription(Map input) {
      return null
    }
  }

  static class Converter2 implements AtomicOperationConverter {
    AtomicOperation convertOperation(Map input) {
      new Op2()
    }

    Object convertDescription(Map input) {
      return null
    }
  }
}
