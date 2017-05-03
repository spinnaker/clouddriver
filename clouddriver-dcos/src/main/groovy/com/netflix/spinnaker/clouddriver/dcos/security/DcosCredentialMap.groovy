package com.netflix.spinnaker.clouddriver.dcos.security

class DcosCredentialMap {
    final List<DcosClusterCredentials> dcosClusterCredentials

    public DcosCredentialMap(List<DcosClusterCredentials> clusterCredentials) {
        dcosClusterCredentials = new ArrayList<>()

        clusterCredentials?.forEach({ dcosClusterCredentials.add(it)})
    }

    public DcosClusterCredentials getCredentialsByCluster(String cluster) {
        dcosClusterCredentials.stream().find {it.name == cluster} as DcosClusterCredentials
    }

    public List<DcosClusterCredentials> getCredentials() {
        dcosClusterCredentials
    }
}
