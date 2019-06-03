package com.netflix.spinnaker.clouddriver.tencent.deploy.ops

import com.netflix.spinnaker.clouddriver.tencent.deploy.description.EnableDisableTencentServerGroupDescription

/*
curl -X POST \
  http://localhost:7002/tencent/ops \
  -H 'Content-Type: application/json' \
  -H 'cache-control: no-cache' \
  -d '[
    {
        "enableServerGroup": {
        	"accountName": "test",
            "serverGroupName": "myapp-dev-v007",
            "region": "ap-guangzhou",
            "credentials": "test"
        }
    }
]'
*/

class EnableTencentServerGroupAtomicOperation extends AbstractEnableDisableAtomicOperation {
  final String basePhase = "ENABLE_SERVER_GROUP"
  final boolean disable = false

  EnableTencentServerGroupAtomicOperation (EnableDisableTencentServerGroupDescription description) {
    super(description)
  }
}
