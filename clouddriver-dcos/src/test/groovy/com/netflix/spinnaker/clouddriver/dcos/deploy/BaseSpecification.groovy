package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.spinnaker.clouddriver.dcos.DcosCredentials
import mesosphere.dcos.client.Config
import spock.lang.Specification

class BaseSpecification extends Specification {
  def defaultCredentialsBuilder() {
    DcosCredentials.builder().name('test').environment('test').accountType('test').dcosUrl('https://test.url.com')
      .secretStore('default').dockerRegistries([]).requiredGroupMembership([]).dcosClientConfig(Config.builder().build())
  }
}
