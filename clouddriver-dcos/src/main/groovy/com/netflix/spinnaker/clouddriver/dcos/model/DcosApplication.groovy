package com.netflix.spinnaker.clouddriver.dcos.model

import com.netflix.spinnaker.clouddriver.model.Application
import groovy.transform.Canonical

@Canonical
class DcosApplication implements Application, Serializable {
  String name
  Map<String, Set<String>> clusterNames = Collections.synchronizedMap(new HashMap<String, Set<String>>())
  Map<String, String> attributes = Collections.synchronizedMap(new HashMap<String, String>())
}
