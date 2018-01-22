/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.model.AmazonImage
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import com.netflix.spinnaker.clouddriver.aws.data.Keys

class AmazonImageProviderSpec extends Specification {
  Cache cache = Mock(Cache)

  @Subject
  AmazonImageProvider provider = new AmazonImageProvider(cache)

  void "should return one image"() {

    when:
    def result = provider.getImageById("ami-123321")

    then:
    result == Optional.of(Artifact.builder()
            .name("some_ami")
            .type(AmazonImage.AMAZON_IMAGE_TYPE)
            .location("123456789/eu-west-1")
            .metadata("name": "some_ami", ownerId: '123456789', "serverGroups": [], "imageId": "ami-123321", "region": "eu-west-1", "account": "test_account")
            .reference("ami-123321")
            .build())

    and:
    1 * cache.filterIdentifiers(IMAGES.ns, _ as String) >> [
            "aws:images:test_account:eu-west-1:ami-123321"
    ]

    1 * cache.getAll(IMAGES.ns, ["aws:images:test_account:eu-west-1:ami-123321"]) >>
            [imageCacheData('aws:images:test_account:eu-west-1:ami-123321', [
                    account: 'test_account',
                    region : 'eu-west-1',
                    name   : 'some_ami',
                    ownerId: '123456789',
                    imageId: 'ami-123321'])]
  }

  void "should not find any image"() {
    when:
    def result = provider.getImageById("ami-123321")

    then:
    result == Optional.empty()

    and:
    1 * cache.filterIdentifiers(IMAGES.ns, _ as String) >> []
  }

  void "should throw exception of invalid ami name"() {
    when:
    provider.getImageById("amiz-123321")

    then:
    thrown(RuntimeException)
  }

  static private CacheData imageCacheData(String imageId, Map attributes) {
    new DefaultCacheData(Keys.getImageKey(imageId, attributes.account, attributes.region), attributes, [:])
  }
}
