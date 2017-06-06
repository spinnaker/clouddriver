package com.netflix.spinnaker.clouddriver.dcos.security

import com.netflix.spinnaker.clouddriver.dcos.DcosClientCompositeKey
import com.netflix.spinnaker.clouddriver.dcos.DcosConfigurationProperties
import mesosphere.dcos.client.Config

class DcosClusterCredentials {
    private final static DEFAULT_SECRET_STORE = 'default'
    final String name
    final String account
    final String cluster
    final String dcosUrl
    final String secretStore
    final List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries
    final Config dcosConfig

    private DcosClusterCredentials(Builder builder) {
        name = builder.key.cluster
        account = builder.key.account
        cluster = builder.key.cluster
        dcosUrl = builder.dcosUrl
        secretStore = builder.secretStore ? builder.secretStore : DEFAULT_SECRET_STORE
        dockerRegistries = builder.dockerRegistries
        dcosConfig = builder.dcosConfig
    }

    public static Builder builder() {
        return new Builder()
    }

    public static class Builder {
        private DcosClientCompositeKey key
        private String dcosUrl
        private String secretStore
        private List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries
        private Config dcosConfig

        public Builder key(DcosClientCompositeKey key) {
            this.key = key
            this
        }

        public Builder dcosUrl(String dcosUrl) {
            this.dcosUrl = dcosUrl
            this
        }

        public Builder secretStore(String secretStore) {
            this.secretStore = secretStore
            this
        }

        public Builder dockerRegistries(List<DcosConfigurationProperties.LinkedDockerRegistryConfiguration> dockerRegistries) {
            this.dockerRegistries = dockerRegistries
            this
        }

        public Builder dcosConfig(Config dcosConfig) {
            this.dcosConfig = dcosConfig
            this
        }

        public DcosClusterCredentials build() {
            return new DcosClusterCredentials(this)
        }
    }
}
