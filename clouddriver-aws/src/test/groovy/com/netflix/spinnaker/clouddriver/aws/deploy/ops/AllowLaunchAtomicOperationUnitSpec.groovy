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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DeleteTagsRequest
import com.amazonaws.services.ec2.model.DescribeImagesRequest
import com.amazonaws.services.ec2.model.DescribeImagesResult
import com.amazonaws.services.ec2.model.DescribeTagsResult
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TagDescription
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

class AllowLaunchAtomicOperationUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "image amiId is resolved from name"() {
    setup:
    def ec2 = Mock(AmazonEC2)
    def provider = Stub(AmazonClientProvider) {
      getAmazonEC2(_, _, true) >> ec2
    }

    def target = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '12345'
    }

    def source = Stub(NetflixAmazonCredentials) {
      getAccountId() >> '67890'
    }

    def creds = Stub(AccountCredentialsProvider) {
      getCredentials('target') >> target
    }
    def op = new AllowLaunchAtomicOperation(new AllowLaunchDescription(amiName: 'super-awesome-ami', account: 'target', credentials: source))
    op.accountCredentialsProvider = creds
    op.amazonClientProvider = provider

    when:
    op.operate([])

    then:
    ec2.describeTags(_) >> new DescribeTagsResult()
    1 * ec2.describeImages(_) >> { DescribeImagesRequest dir ->
        assert dir.executableUsers
        assert dir.executableUsers.size() == 1
        assert dir.executableUsers.first() == '12345'
        assert dir.filters
        assert dir.filters.size() == 1
        assert dir.filters.first().name == 'name'
        assert dir.filters.first().values == ['super-awesome-ami']

        new DescribeImagesResult().withImages(new Image().withImageId('ami-12345').withOwnerId('67890'))
    }
  }

  void "image attribute modification is invoked on request"() {
    setup:
    def prodCredentials = TestCredential.named('prod')
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(AmazonEC2) {
      describeTags(_) >> new DescribeTagsResult()
      describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId('ami-123456').withOwnerId(testCredentials.accountId))
    }
    def targetAmazonEc2 = Mock(AmazonEC2) {
      describeTags(_) >> new DescribeTagsResult()
      describeImages(_) >> null
    }
    def provider = Mock(AmazonClientProvider)
    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider){
      1 * getCredentials("prod") >> prodCredentials
    }
    with(provider) {
      1 * getAmazonEC2(testCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(prodCredentials, _, true) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * modifyImageAttribute(_) >> { ModifyImageAttributeRequest request ->
        assert request.launchPermission.add.get(0).userId == prodCredentials.accountId
      }
    }
  }

  void "should replicate tags"() {
    def prodCredentials = TestCredential.named('prod')
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(account: "prod", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider){
      1 * getCredentials("prod") >> prodCredentials
    }
    with(provider) {
      1 * getAmazonEC2(testCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(prodCredentials, _, true) >> targetAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-123456").withOwnerId(testCredentials.accountId))
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b:"1", c: "2"])
      1 * deleteTags(new DeleteTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "1"), new Tag(key: "c", value: "2")]))
      1 * createTags(new CreateTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "2")]))
    }
  }

  void "should skip tag replication when target account is the same as the requesting account"() {
    def testCredentials = TestCredential.named('test')

    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)
    def provider = Mock(AmazonClientProvider)

    def description = new AllowLaunchDescription(account: "test", amiName: "ami-123456", region: "us-west-1", credentials: testCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = provider
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider){
      1 * getCredentials("test") >> testCredentials
    }
    with(provider) {
      1 * getAmazonEC2(testCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(testCredentials, _, true) >> targetAmazonEc2
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-123456").withOwnerId(testCredentials.accountId))
    }

    0 * _
  }

  void "should lookup owner account of resolved ami"() {
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target')

    def ownerAmazonEc2 = Mock(AmazonEC2)
    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)

    def description = new AllowLaunchDescription(account: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider) {
      1 * getCredentials('target') >> targetCredentials
      1 * getAll() >> [sourceCredentials, targetCredentials, ownerCredentials]
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2(sourceCredentials, _, true) >> sourceAmazonEc2
      1 * getAmazonEC2(targetCredentials, _, true) >> targetAmazonEc2
      1 * getAmazonEC2(ownerCredentials, _, true) >> ownerAmazonEc2
    }
    with(sourceAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(new Image().withImageId("ami-123456").withOwnerId(ownerCredentials.accountId))
    }
    with(ownerAmazonEc2) {
      1 * modifyImageAttribute(_)
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b: "2"])
    }
    with(targetAmazonEc2) {
      1 * describeTags(_) >> constructDescribeTagsResult([a:"1", b:"1", c: "2"])
      1 * deleteTags(new DeleteTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "1"), new Tag(key: "c", value: "2")]))
      1 * createTags(new CreateTagsRequest(resources: ["ami-123456"], tags: [new Tag(key: "b", value: "2")]))
    }

  }

  void "should return resolved public AMI without performing additional operations"() {
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def targetCredentials = TestCredential.named('target')
    def ownerAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)

    def description = new AllowLaunchDescription(account: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: ownerCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:

    with(op.accountCredentialsProvider) {
      1 * getCredentials('target') >> targetCredentials
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2(targetCredentials, _, true) >> targetAmazonEc2
      1 * getAmazonEC2(ownerCredentials, _, true) >> ownerAmazonEc2
    }

    with(targetAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(
        new Image()
          .withImageId("ami-123456")
          .withOwnerId(ownerCredentials.accountId)
          .withPublic(true))
    }
    0 * _

  }

  void 'should return resolved third-party private AMI when account allows'() {
    setup:
    def sourceCredentials = TestCredential.named('source')
    def targetCredentials = TestCredential.named('target', [allowPrivateThirdPartyImages: true])
    def sourceAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)

    def description = new AllowLaunchDescription(account: 'target', amiName: 'ami-123456', region: 'us-west-2', credentials: sourceCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider) {
      1 * getCredentials('target') >> targetCredentials
    }
    with(op.amazonClientProvider) {
      1 * getAmazonEC2(targetCredentials, _, true) >> targetAmazonEc2
      1 * getAmazonEC2(sourceCredentials, _, true) >> sourceAmazonEc2
    }
    with(targetAmazonEc2) {
      3 * describeImages(_) >> new DescribeImagesResult()
    }
    with(sourceAmazonEc2) {
      3 * describeImages(_) >> new DescribeImagesResult()
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(
        new Image()
          .withImageId('ami-123456')
          .withOwnerId('thirdparty')
      )
    }
  }

  Closure<DescribeTagsResult> constructDescribeTagsResult = { Map tags ->
    new DescribeTagsResult(tags: tags.collect {new TagDescription(key: it.key, value: it.value) })
  }
  
  void "should return resolved target AMI without performing additional operations"() {  
    setup:
    def ownerCredentials = TestCredential.named('owner')
    def targetCredentials = TestCredential.named('target')
    def ownerAmazonEc2 = Mock(AmazonEC2)
    def targetAmazonEc2 = Mock(AmazonEC2)

    def description = new AllowLaunchDescription(account: 'target', amiName: 'ami-123456', region: 'us-west-1', credentials: ownerCredentials)
    def op = new AllowLaunchAtomicOperation(description)
    op.amazonClientProvider = Mock(AmazonClientProvider)
    op.accountCredentialsProvider = Mock(AccountCredentialsProvider)

    when:
    op.operate([])

    then:
    with(op.accountCredentialsProvider) {
      1 * getCredentials('target') >> targetCredentials
    }

    with(op.amazonClientProvider) {
      1 * getAmazonEC2(targetCredentials, _, true) >> targetAmazonEc2
      1 * getAmazonEC2(ownerCredentials, _, true) >> ownerAmazonEc2
    }
    with(targetAmazonEc2) {
      1 * describeImages(_) >> new DescribeImagesResult().withImages(
        new Image()
          .withImageId("ami-123456")
          .withOwnerId(targetCredentials.accountId))	  
    }
    0 * _
    
  }
  
}
