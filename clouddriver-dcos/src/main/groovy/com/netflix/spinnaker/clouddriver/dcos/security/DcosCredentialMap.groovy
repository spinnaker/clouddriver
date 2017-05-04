package com.netflix.spinnaker.clouddriver.dcos.security

class DcosCredentialMap {
    final Map<String, DcosClusterCredentials> dcosClusterCredentials

    public DcosCredentialMap(List<DcosClusterCredentials> clusterCredentials) {
        dcosClusterCredentials = new HashMap<>()

        clusterCredentials?.forEach({ dcosClusterCredentials.putIfAbsent(it.name, it)})
    }

    public DcosClusterCredentials getCredentialsByCluster(String cluster) {
        dcosClusterCredentials.get(cluster)
    }

    public List<DcosClusterCredentials> getCredentials() {
        dcosClusterCredentials.values()
    }
}
