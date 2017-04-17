package com.netflix.spinnaker.clouddriver.dcos.deploy

import com.netflix.spinnaker.clouddriver.dcos.security.DcosCredentials
import mesosphere.dcos.client.Config
import spock.lang.Specification

import static com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties.LinkedDockerRegistryConfiguration

class BaseSpecification extends Specification {
  def defaultCredentialsBuilder() {
    DcosCredentials.builder().name('test').environment('test').accountType('test').dcosUrl('https://test.url.com')
      .secretStore('default').dockerRegistries([new LinkedDockerRegistryConfiguration(accountName: 'dockerReg')]).requiredGroupMembership([]).dcosClientConfig(Config.builder().build())
  }
}
