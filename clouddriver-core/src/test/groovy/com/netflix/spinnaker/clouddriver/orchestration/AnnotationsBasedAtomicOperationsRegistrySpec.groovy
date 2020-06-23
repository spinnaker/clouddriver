/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.orchestration

import com.netflix.spinnaker.clouddriver.core.CloudProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.security.ProviderVersion
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

import java.lang.annotation.Annotation

class AnnotationsBasedAtomicOperationsRegistrySpec extends Specification {

  @Subject
  AtomicOperationsRegistry atomicOperationsRegistry = new AnnotationsBasedAtomicOperationsRegistry(
    applicationContext: new AnnotationConfigApplicationContext(TestConfiguration),
    cloudProviders: [new MyCloudProvider()]
  )

  void 'annotations based registry should return the converter if the specified name matches the component name'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('operationOldDescription', null, ProviderVersion.v1)

    then:
    noExceptionThrown()
    converter != null
    converter instanceof TestConverter
  }

  void 'annotations based registry should fail to return the converter if the specified name matches the component name but an incorrect version is used'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('operationOldDescription', null, ProviderVersion.v2)

    then:
    thrown(AtomicOperationConverterNotFoundException)
    converter == null
  }


  void 'annotations based registry should return the converter that matches the AtomicOperationDescription name and cloud provider and default version'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('operationDescription', 'test-provider', ProviderVersion.v1)

    then:
    noExceptionThrown()
    converter != null
    converter instanceof TestConverter
  }

  void 'annotations based registry should return the converter that matches the AtomicOperationDescription name and cloud provider and version'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('operationDescription', 'test-provider', ProviderVersion.v2)

    then:
    noExceptionThrown()
    converter != null
    converter instanceof TestConverterV2
  }

  void 'annotations based registry should throw a NoSuchBeanDefinitionException if no converter found for given name with no cloud provider specified'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('foo', null, ProviderVersion.v1)

    then:
    thrown(NoSuchBeanDefinitionException)
    converter == null
  }

  void 'annotations based registry should throw an AtomicOperationConverterNotFoundException if no converter found for given name with cloud provider specified'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('foo', 'test-provider', ProviderVersion.v1)

    then:
    thrown(AtomicOperationConverterNotFoundException)
    converter == null
  }

  void 'uncallable converter should reject any version'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('noOperationDescription', 'test-provider', ProviderVersion.v1)

    then:
    thrown(AtomicOperationConverterNotFoundException)
    converter == null

    when:
    converter = atomicOperationsRegistry.getAtomicOperationConverter('noOperationDescription', 'test-provider', ProviderVersion.v2)

    then:
    thrown(AtomicOperationConverterNotFoundException)
    converter == null
  }

  void 'annotations based registry should return the validator if the specified name matches the component name'() {
    when:
    def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator('operationOldDescriptionValidator', null, ProviderVersion.v1)

    then:
    noExceptionThrown()
    validator != null
    validator instanceof TestValidator
  }

  void 'annotations based registry should return the validator that matches the AtomicOperationDescription name and cloud provider'() {
    when:
    def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator('operationDescriptionValidator', 'test-provider', ProviderVersion.v1)

    then:
    noExceptionThrown()
    validator != null
    validator instanceof TestValidator
  }

  void 'annotations based registry should return a null if no validator found for given name with no cloud provider specified'() {
    when:
    def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator('foo', null, ProviderVersion.v1)

    then:
    noExceptionThrown()
    validator == null
  }

  void 'annotations based registry should return a null if no validator found for given name with no version specified'() {
    when:
    def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator('foo', 'test-provider', null)

    then:
    noExceptionThrown()
    validator == null
  }

  void 'annotations based registry should return a null if no validator found for given name with cloud provider specified'() {
    when:
    def validator = atomicOperationsRegistry.getAtomicOperationDescriptionValidator('foo', 'test-provider', ProviderVersion.v1)

    then:
    noExceptionThrown()
    validator == null
  }

  void 'should return matching converter based on description version'() {
    when:
    def converter = atomicOperationsRegistry.getAtomicOperationConverter('versionedDescription', 'test-provider', ProviderVersion.v1)

    then:
    noExceptionThrown()
    converter instanceof VersionedDescriptionConverter

    when:
    converter = atomicOperationsRegistry.getAtomicOperationConverter('versionedDescription@v2', 'test-provider', ProviderVersion.v1)

    then:
    noExceptionThrown()
    converter instanceof VersionedDescriptionV2Converter
  }

  static class MyCloudProvider implements CloudProvider {
    String id = 'test-provider'
    String displayName = 'Test Provider'
    Class<? extends Annotation> operationAnnotationType = TestProviderOperation
  }

  @Configuration
  static class TestConfiguration {
    @Bean(name = "operationOldDescription")
    AtomicOperationConverter testConverter() {
      new TestConverter()
    }

    @Bean(name = "operationOldDescriptionV2")
    AtomicOperationConverter testConverterV2() {
      new TestConverterV2()
    }

    @Bean(name = "uncallableOperation")
    AtomicOperationConverter testNothingConverter() {
      new TestNothingConverter()
    }

    @Bean(name = "operationOldDescriptionValidator")
    DescriptionValidator descriptionValidator() {
      new TestValidator()
    }

    @Bean(name = "operationOldDescriptionValidatorV2")
    DescriptionValidator descriptionValidatorV2() {
      new TestValidatorV2()
    }

    @Bean(name = "versionedConverterV1")
    AtomicOperationConverter versionedConverter() {
      new VersionedDescriptionConverter()
    }

    @Bean(name = "versionedConverterV2")
    AtomicOperationConverter versionedConverterV2() {
      new VersionedDescriptionV2Converter()
    }
  }

  @TestProviderOperation("operationDescription")
  static class TestConverter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      return null
    }
    @Override
    Object convertDescription(Map input) {
      return null
    }
  }

  @TestProviderOperation("operationDescription")
  static class TestValidator extends DescriptionValidator {
    @Override
    void validate(List priorDescriptions, Object description, Errors errors) {
    }
  }

  @TestProviderOperation("operationDescription")
  static class TestConverterV2 implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      return null
    }
    @Override
    Object convertDescription(Map input) {
      return null
    }
    @Override
    boolean acceptsVersion(ProviderVersion version) {
      return version == ProviderVersion.v2
    }
  }

  @TestProviderOperation("operationDescription")
  static class TestValidatorV2 extends DescriptionValidator {
    @Override
    void validate(List priorDescriptions, Object description, Errors errors) {
    }
    @Override
    boolean acceptsVersion(ProviderVersion version) {
      return version == ProviderVersion.v2
    }
  }

  @TestProviderOperation("noOperationDescription")
  static class TestNothingConverter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      return null
    }
    @Override
    Object convertDescription(Map input) {
      return null
    }
    @Override
    boolean acceptsVersion(ProviderVersion version) {
      return false
    }
  }

  @TestProviderOperation("versionedDescription")
  static class VersionedDescriptionConverter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      return null
    }
    @Override
    Object convertDescription(Map input) {
      return null
    }
    @Override
    boolean acceptsVersion(String version) {
      return version == null || version == "v1"
    }
  }

  @TestProviderOperation("versionedDescription@v2")
  static class VersionedDescriptionV2Converter implements AtomicOperationConverter {
    @Override
    AtomicOperation convertOperation(Map input) {
      return null
    }
    @Override
    Object convertDescription(Map input) {
      return null
    }
    @Override
    boolean acceptsVersion(String version) {
      return version == "v2"
    }
  }


}
