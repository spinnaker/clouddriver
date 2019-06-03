package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription

/*
curl -X POST \
  http://localhost:7002/tencent/ops \
  -H 'Content-Type: application/json' \
  -H 'cache-control: no-cache' \
  -d '[
    {
        "disableServerGroup": {
        	"accountName": "test",
            "serverGroupName": "myapp-dev-v007",
            "region": "ap-guangzhou",
            "credentials": "test"
        }
    }
]'
*/

class DisableTencentServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {
  final String basePhase = "DISABLE_SERVER_GROUP"
  boolean disable = true

  DisableTencentServerGroupAtomicOperation(EnableDisableTencentServerGroupDescription description) {
    super(description)
  }
}
