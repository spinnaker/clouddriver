# AWS Lambda Support

### **Background **

Spinnaker CloudDriver has been enhanced to add support for AWS Lambda. Below lists the API contract input that have been coded in this repository.

## clouddriver.yml override ##

```yaml
aws:
  lambda:
    enabled: true
  accounts:
    - name: test
      lambdaEnabled: true
```

# Controller calls

## Get all lambda functions

### Purpose

Retrieves all cached lambda functions.

***Sample Request***

```
curl -X GET --header 'Accept: application/json'
'http://localhost:7002/functions'
```

***Sample Response***

```
`[
  {
    "accountName": "spinnaker-lambda",
    "codeSha256": null,
    "codeSize": null,
    "deadLetterConfig": null,
    "description": "Encryption",
    "environment": null,
    "functionArn": "arn:aws:lambda:us-west-2:123456789012:function:Encryption",
    "functionName": "Encryption",
    "handler": "lambda_function.lambda_handler",
    "kmskeyArn": null,
    "lastModified": "2017-01-12T18:44:57.457+0000",
    "masterArn": null,
    "memorySize": null,
    "region": "us-west-2",
    "revisionId": "6ee650df-804b-4a7b-a9a4-b14fb316a358",
    "role": null,
    "runtime": "python2.7",
    "timeout": null,
    "tracingConfig": null,
    "version": null,
    "vpcConfig": null
  }
]`
```

# Operations

## Create a Lambda function

### Purpose

Creates a lambda function. Implement https://docs.aws.amazon.com/lambda/latest/dg/API_CreateFunction.html

***Sample Request***

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json'
-d '{ \
 "application":"SAMPLE-NEW-LAMBDA-PRINT-FUNCTION", \
 "region":"us-west-2", \
 "stack":"stack Goes here", \
 "freeFormDetails":"", \
 "credentials":"spinnaker-lambda", \
 "description":"THIS IS THE SAMPLE DESCRIPTION", \
 "s3bucket": "aws-codestar-us-west-2-123456789012", \
 "s3key": "version-1.zip", \
 "handler": "lambda_function.lambda_handler", \
 "memory": 512, \
 "publish": true, \
 "role": "arn:aws:iam::123456789012:role/lambda-deployer-anuj", \
 "runtime": "python3.6", \
 "timeout": 60 \
 }' 'http://localhost:7002/awslambda/ops/createAwsLambda'
```


***Request Body***

```
{
    "application": "SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "region": "us-west-2",
    "stack": "stack Goes here",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "description": "THIS IS THE SAMPLE DESCRIPTION",
    "s3bucket": "aws-codestar-us-west-2-123456789012",
    "s3key": "version-1.zip",
    "handler": "lambda_function.lambda_handler",
    "memory": 512,
    "publish": true,
    "role": "arn:aws:iam::123456789012:role/lambda-deployer-anuj",
    "runtime": "python3.6",
    "timeout": 60,
    "tags": [{
            "key1":"value1"
        },
        {
            "key2":"value2"
        },
        {
            "key3":"value3"
        }
    ]


}
```

***Sample Response***


```
`{
  "id": "22d0d91e-0763-4d30-bb36-9531fbb88991",
  "resourceUri": "/task/22d0d91e-0763-4d30-bb36-9531fbb88991"
}`
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: CreateLambdaAtomicOperation"
  }, {
    "phase" : "CREATE_LAMBDA_FUNCTION",
    "status" : "Initializing Creation of AWS Lambda Function Operation..."
  }, {
    "phase" : "CREATE_LAMBDA_FUNCTION",
    "status" : "Finished Creation of AWS Lambda Function Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "22d0d91e-0763-4d30-bb36-9531fbb88991",
  "ownerId" : "532f8b07-dace-481c-a42f-2ebc4f3fe212@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "codeSha256" : "nORwpqOrQUcFjNCwoSIoxMOxb8O7k136X6VJUYGiiz8=",
    "codeSize" : 211,
    "description" : "THIS IS THE SAMPLE DESCRIPTION",
    "functionArn" : "arn:aws:lambda:us-west-2:123456789012:function:SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "functionName" : "SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "handler" : "lambda_function.lambda_handler",
    "lastModified" : "2018-09-13T17:57:24.925+0000",
    "memorySize" : 512,
    "revisionId" : "6702711b-a767-4fe2-a8de-9ad14e56e80b",
    "role" : "arn:aws:iam::123456789012:role/lambda-deployer-anuj",
    "runtime" : "python3.6",
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Length" : "670",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 17:57:25 GMT",
        "x-amzn-RequestId" : "785c30ce-b77e-11e8-9e38-1bdb307ac899"
      },
      "httpStatusCode" : 201
    },
    "sdkResponseMetadata" : {
      "requestId" : "785c30ce-b77e-11e8-9e38-1bdb307ac899"
    },
    "timeout" : 60,
    "tracingConfig" : {
      "mode" : "PassThrough"
    },
    "version" : "1"
  } ],
  "startTimeMs" : 1536861444695,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```




## Update a Lambda function CONFIGURATION

### Purpose

Updates the configuration (and NOT code) of the specified lambda function. Implement https://docs.aws.amazon.com/lambda/latest/dg/API_UpdateFunctionConfiguration.html

***Sample Request***

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json'
-d '{ \
 "application":"SAMPLE-NEW-LAMBDA-PRINT-FUNCTION", \
 "region":"us-west-2", \
 "stack":"stack Goes here", \
 "freeFormDetails":"", \
 "credentials":"spinnaker-lambda", \
 "description":"THIS IS THE SAMPLE DESCRIPTION UPDATED", \
 "memory": 512, \
 "role": "arn:aws:iam::123456789012:role/lambda-deployer-anuj", \
 "runtime": "python3.6", \
 "timeout": 60, \
 "handler": "lambda_function.lambda_handler" \
 }' 'http://localhost:7002/awslambda/ops/updateAwsLambdaConfiguration'
```

***Sample Request Body***

```
{
    "application": "SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "region": "us-west-2",
    "stack": "stack Goes here",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "description": "THIS IS THE SAMPLE DESCRIPTION UPDATED",
    "memory": 512,
    "role": "arn:aws:iam::123456789012:role/lambda-deployer-anuj",
    "runtime": "python3.6",
    "timeout": 60,
    "handler": "lambda_function.lambda_handler"
}
```

***Sample Response***


```
{
  "id": "3c1b111d-fe65-440a-8059-817ac249c708",
  "resourceUri": "/task/3c1b111d-fe65-440a-8059-817ac249c708"
}
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: UpdateLambdaConfigurationAtomicOperation"
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CONFIGURATION",
    "status" : "Initializing Updating of AWS Lambda Function Configuration Operation..."
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CONFIGURATION",
    "status" : "Finished Updating of AWS Lambda Function Configuration Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "3c1b111d-fe65-440a-8059-817ac249c708",
  "ownerId" : "532f8b07-dace-481c-a42f-2ebc4f3fe212@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "codeSha256" : "nORwpqOrQUcFjNCwoSIoxMOxb8O7k136X6VJUYGiiz8=",
    "codeSize" : 211,
    "description" : "THIS IS THE SAMPLE DESCRIPTION UPDATED",
    "functionArn" : "arn:aws:lambda:us-west-2:123456789012:function:SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "functionName" : "SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "handler" : "lambda_function.lambda_handler",
    "lastModified" : "2018-09-13T18:06:48.949+0000",
    "memorySize" : 512,
    "revisionId" : "ed3a1a06-3418-436b-859c-ae011b7e20ab",
    "role" : "arn:aws:iam::123456789012:role/lambda-deployer-anuj",
    "runtime" : "python3.6",
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Length" : "684",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 18:06:49 GMT",
        "x-amzn-RequestId" : "c89ea641-b77f-11e8-85c3-1924bd9d9b79"
      },
      "httpStatusCode" : 200
    },
    "sdkResponseMetadata" : {
      "requestId" : "c89ea641-b77f-11e8-85c3-1924bd9d9b79"
    },
    "timeout" : 60,
    "tracingConfig" : {
      "mode" : "PassThrough"
    },
    "version" : "$LATEST"
  } ],
  "startTimeMs" : 1536862008928,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```



## Update a Lambda function CODE

### Purpose

Updates the code (and NOT configuration) of the specified lambda function and publishes/unpublishes it. Implement https://docs.aws.amazon.com/lambda/latest/dg/API_UpdateFunctionCode.html

***Sample Request***

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json'
-d '{ \
 "application":"SAMPLE-LAMBDA-PRINT-FUNCTION", \
 "region":"us-west-2", \
 "stack":"stack Goes here", \
 "freeFormDetails":"", \
 "credentials":"spinnaker-lambda", \
 "s3bucket": "aws-codestar-us-west-2-123456789012", \
 "s3key": "sample_print_function_v2.zip", \
 "publish": true \
 }' 'http://localhost:7002/awslambda/ops/updateAwsLambdaCode'


```

***Sample Request Body***

```
{
 "application":"SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
 "region":"us-west-2",
 "stack":"stack Goes here",
 "freeFormDetails":"",
 "credentials":"spinnaker-lambda",
 "s3bucket": "aws-codestar-us-west-2-123456789012",
 "s3key": "sample_print_function_v2.zip",
 "publish": true
 }
```

***Sample Response***


```
{
  "id": "d32d6dec-20a1-4ce1-a69f-f59d1f0abea8",
  "resourceUri": "/task/d32d6dec-20a1-4ce1-a69f-f59d1f0abea8"
}
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: UpdateLambdaCodeAtomicOperation"
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Initializing Updating of AWS Lambda Function Code Operation..."
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Finished Updating of AWS Lambda Function Code Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "d32d6dec-20a1-4ce1-a69f-f59d1f0abea8",
  "ownerId" : "532f8b07-dace-481c-a42f-2ebc4f3fe212@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "codeSha256" : "nORwpqOrQUcFjNCwoSIoxMOxb8O7k136X6VJUYGiiz8=",
    "codeSize" : 211,
    "description" : "THIS IS THE SAMPLE DESCRIPTION MODIFIED",
    "functionArn" : "arn:aws:lambda:us-west-2:123456789012:function:SAMPLE-LAMBDA-PRINT-FUNCTION:2",
    "functionName" : "SAMPLE-LAMBDA-PRINT-FUNCTION",
    "handler" : "lambda_function.lambda_handler",
    "lastModified" : "2018-09-13T19:01:26.382+0000",
    "memorySize" : 512,
    "revisionId" : "fe631f6d-4495-4bf7-9031-3039247fd40a",
    "role" : "arn:aws:iam::123456789012:role/lambda-deployer-anuj",
    "runtime" : "python3.6",
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Length" : "673",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 19:01:26 GMT",
        "x-amzn-RequestId" : "6a0720df-b787-11e8-a3c2-d789463f16c1"
      },
      "httpStatusCode" : 200
    },
    "sdkResponseMetadata" : {
      "requestId" : "6a0720df-b787-11e8-a3c2-d789463f16c1"
    },
    "timeout" : 60,
    "tracingConfig" : {
      "mode" : "PassThrough"
    },
    "version" : "2"
  } ],
  "startTimeMs" : 1536865286195,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```



## Invoke Lambda Function

### Purpose

Invoke https://docs.aws.amazon.com/lambda/latest/dg/API_Invoke.html . Payload is sent in freeFormDetails property.

```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{ \
     "application": "DEMO-FUNCTION-2", \
     "region": "us-west-2", \
     "stack": "stack Goes here", \
     "freeFormDetails": "{\"PROPERTY1_OF_PAYLOAD\":\"PROPERTY VALUE OF PAYLOAD\"}", \
     "credentials": "spinnaker-lambda", \
     "aliasname" : "test" \
 }' 'http://localhost:7002/awslambda/ops/invokeAwsLambda'

```

Value of aliasname is optional.  “” or “ “ will result in execution of $LATEST version.
The property name mandatory

***Sample Request Body***

```
{
    "application": "SAMPLE-LAMBDA-PRINT-FUNCTION",
    "region": "us-west-2",
    "stack": "stack Goes here",
    "freeFormDetails": "{\"PROPERTY1_OF_PAYLOAD\":\"PROPERTY VALUE OF PAYLOAD\"}",
    "credentials": "spinnaker-lambda"
}
```

***Sample Response***


```
{
  "id": "87cacb87-f59e-4375-b1b4-00dcdb7ee7bf",
  "resourceUri": "/task/87cacb87-f59e-4375-b1b4-00dcdb7ee7bf"
}
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: InvokeLambdaAtomicOperation"
  }, {
    "phase" : "INVOKE_LAMBDA_FUNCTION",
    "status" : "Initializing Invoking AWS Lambda Function Operation..."
  }, {
    "phase" : "INVOKE_LAMBDA_FUNCTION",
    "status" : "Finished Invoking of AWS Lambda Function Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "87cacb87-f59e-4375-b1b4-00dcdb7ee7bf",
  "ownerId" : "532f8b07-dace-481c-a42f-2ebc4f3fe212@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "executedVersion" : "$LATEST",
    "logResult" : "U1RBUlQgUmVxdWVzdElkOiBmY2M2NDRiOC1iNzg3LTExZTgtOTQyNC01NzExNDBjMGRhYzkgVmVyc2lvbjogJExBVEVTVAp7J1BST1BFUlRZMV9PRl9QQVlMT0FEJzogJ1BST1BFUlRZIFZBTFVFIE9GIFBBWUxPQUQnfQo8X19tYWluX18uTGFtYmRhQ29udGV4dCBvYmplY3QgYXQgMHg3ZjBhOTc1NzZlZjA+CkVORCBSZXF1ZXN0SWQ6IGZjYzY0NGI4LWI3ODctMTFlOC05NDI0LTU3MTE0MGMwZGFjOQpSRVBPUlQgUmVxdWVzdElkOiBmY2M2NDRiOC1iNzg3LTExZTgtOTQyNC01NzExNDBjMGRhYzkJRHVyYXRpb246IDAuNzYgbXMJQmlsbGVkIER1cmF0aW9uOiAxMDAgbXMgCU1lbW9yeSBTaXplOiA1MTIgTUIJTWF4IE1lbW9yeSBVc2VkOiAzMSBNQgkK",
    "payload" : "eyJQUk9QRVJUWTFfT0ZfUEFZTE9BRCI6ICJQUk9QRVJUWSBWQUxVRSBPRiBQQVlMT0FEIn0=",
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Length" : "53",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 19:05:32 GMT",
        "X-Amz-Executed-Version" : "$LATEST",
        "X-Amz-Log-Result" : "U1RBUlQgUmVxdWVzdElkOiBmY2M2NDRiOC1iNzg3LTExZTgtOTQyNC01NzExNDBjMGRhYzkgVmVyc2lvbjogJExBVEVTVAp7J1BST1BFUlRZMV9PRl9QQVlMT0FEJzogJ1BST1BFUlRZIFZBTFVFIE9GIFBBWUxPQUQnfQo8X19tYWluX18uTGFtYmRhQ29udGV4dCBvYmplY3QgYXQgMHg3ZjBhOTc1NzZlZjA+CkVORCBSZXF1ZXN0SWQ6IGZjYzY0NGI4LWI3ODctMTFlOC05NDI0LTU3MTE0MGMwZGFjOQpSRVBPUlQgUmVxdWVzdElkOiBmY2M2NDRiOC1iNzg3LTExZTgtOTQyNC01NzExNDBjMGRhYzkJRHVyYXRpb246IDAuNzYgbXMJQmlsbGVkIER1cmF0aW9uOiAxMDAgbXMgCU1lbW9yeSBTaXplOiA1MTIgTUIJTWF4IE1lbW9yeSBVc2VkOiAzMSBNQgkK",
        "X-Amzn-Trace-Id" : "root=1-5b9ab4fc-7a7efa57662b8c42048eae83;sampled=0",
        "x-amzn-Remapped-Content-Length" : "0",
        "x-amzn-RequestId" : "fcc644b8-b787-11e8-9424-571140c0dac9"
      },
      "httpStatusCode" : 200
    },
    "sdkResponseMetadata" : {
      "requestId" : "fcc644b8-b787-11e8-9424-571140c0dac9"
    },
    "statusCode" : 200
  } ],
  "startTimeMs" : 1536865532396,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```



## Delete Lambda Function

### Purpose

Implement https://docs.aws.amazon.com/lambda/latest/dg/API_DeleteFunction.html

***Sample Request***


```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json'
-d '{ \
 "application":"SAMPLE-NEW-LAMBDA-PRINT-FUNCTION", \
 "region":"us-west-2", \
 "stack":"", \
 "freeFormDetails":"", \
 "credentials":"spinnaker-lambda" \
 }' 'http://localhost:7002/awslambda/ops/deleteAwsLambda'
```

***Sample Request Body***

```
{
    "application": "SAMPLE-NEW-LAMBDA-PRINT-FUNCTION",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda"
}
```

***Sample Response***

```
`{
  "id": "151a090e-4a8d-49e2-8398-ae85e2962f45",
  "resourceUri": "/task/151a090e-4a8d-49e2-8398-ae85e2962f45"
}`
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: DeleteLambdaAtomicOperation"
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Initializing deletion of AWS Lambda Function Operation..."
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Finished deletion of AWS Lambda Function  Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "98218a3d-27ce-4b47-a12d-bde8159d5a9c",
  "ownerId" : "5b4f9b88-f992-419b-8604-b88a79e8363f@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 22:40:44 GMT",
        "x-amzn-RequestId" : "0c920374-b7a6-11e8-9895-8b3a73bfc93e"
      },
      "httpStatusCode" : 204
    },
    "sdkResponseMetadata" : {
      "requestId" : "0c920374-b7a6-11e8-9895-8b3a73bfc93e"
    }
  } ],
  "startTimeMs" : 1536878430656,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```



## Upsert  Function Alias

### Purpose

Implement https://docs.aws.amazon.com/lambda/latest/dg/API_CreateAlias.html and https://docs.aws.amazon.com/lambda/latest/dg/API_UpdateAlias.html . These two operations likely will be used in canary deployments . The coded logic checks, if the alias is present in cache. If it does, there will be an update. Else, there will be a create alias request. Request body remains the same.

As the request contracts are the same are create and update alias, these two can be combined in one as upsert.

***Sample Request***


```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{ \
     "application": "LAMBDA-PRINT-FUNCTION", \
     "region": "us-west-2", \
     "stack": "", \
     "freeFormDetails": "", \
     "credentials": "spinnaker-lambda", \
     "aliasdescription" : "description for alias 1", \
     "majorfunctionversion": "$LATEST", \
     "aliasname": "spinnaker-alias-1", \
     "minorfunctionversion" : "", \
     "weighttominorfunctionversion" : "" \
  \
  \
 }' 'http://localhost:7002/awslambda/ops/upsertAwsLambdaAlias'
```

***Sample Request Body***

```
{
    "application": "LAMBDA-PRINT-FUNCTION",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "aliasdescription" : "description for alias 1",
    "majorfunctionversion": "$LATEST",
    "aliasname": "spinnaker-alias-1",
    "minorfunctionversion" : "",
    "weighttominorfunctionversion" : ""


}
```

***Sample Response***

```
`{
  "id": "151a090e-4a8d-49e2-8398-ae85e2962f45",
  "resourceUri": "/task/151a090e-4a8d-49e2-8398-ae85e2962f45"
}`
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: DeleteLambdaAtomicOperation"
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Initializing deletion of AWS Lambda Function Operation..."
  }, {
    "phase" : "UPDATE_LAMBDA_FUNCTION_CODE",
    "status" : "Finished deletion of AWS Lambda Function  Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "98218a3d-27ce-4b47-a12d-bde8159d5a9c",
  "ownerId" : "5b4f9b88-f992-419b-8604-b88a79e8363f@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Type" : "application/json",
        "Date" : "Thu, 13 Sep 2018 22:40:44 GMT",
        "x-amzn-RequestId" : "0c920374-b7a6-11e8-9895-8b3a73bfc93e"
      },
      "httpStatusCode" : 204
    },
    "sdkResponseMetadata" : {
      "requestId" : "0c920374-b7a6-11e8-9895-8b3a73bfc93e"
    }
  } ],
  "startTimeMs" : 1536878430656,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```



## Proposed flow for canary deployment

Implementation of https://docs.aws.amazon.com/lambda/latest/dg/lambda-traffic-shifting-using-aliases.html


* Create a function using createAwsLambda
* Create an alias (named test) pointed version-1 (no weight) using upsertAwsLambdaAlias. Use the following request

```
{
    "application": "NAME_OF_FUNCTION_GOES_HERE",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "aliasdescription" : "DESCRIPTION_OF_ALIAS",
    "majorfunctionversion": "1",
    "aliasname": "NAME_OF_ALIAS",
    "minorfunctionversion" : "",
    "weighttominorfunctionversion" : ""


}
```

* Deploy and publish a new version of code using updateAwsLambdaCode.
* Use upsertAwsLambdaAlias to point 25% of the weight to new version of code. This means that now the alias will route 25% of the traffic to new version and 75% to old version. Use the following request



```
{
    "application": "NAME_OF_FUNCTION_GOES_HERE",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "aliasdescription" : "DESCRIPTION_OF_ALIAS",
    "majorfunctionversion": "1",
    "aliasname": "NAME_OF_ALIAS",
    "minorfunctionversion" : "2",
    "weighttominorfunctionversion" : "0.25"


}
```

* Dial up the % traffic that needs to be routed to new version by 50% using upsertAwsLambdaAlias. This means that now the alias will route 75% of the traffic to new version and 25% to old version. Use the following request



```
{
    "application": "NAME_OF_FUNCTION_GOES_HERE",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "aliasdescription" : "DESCRIPTION_OF_ALIAS",
    "majorfunctionversion": "2",
    "aliasname": "NAME_OF_ALIAS",
    "minorfunctionversion" : "1",
    "weighttominorfunctionversion" : "0.25"


}
```

* Ramp down the traffic from old version of the code using upsertAwsLambdaAlias. That means, all the traffic will now be going to new version only



```
{
    "application": "NAME_OF_FUNCTION_GOES_HERE",
    "region": "us-west-2",
    "stack": "",
    "freeFormDetails": "",
    "credentials": "spinnaker-lambda",
    "aliasdescription" : "DESCRIPTION_OF_ALIAS",
    "majorfunctionversion": "2",
    "aliasname": "NAME_OF_ALIAS",
    "minorfunctionversion" : "1",
    "weighttominorfunctionversion" : "0.00"


}
```



## Upsert Event Mapping

### Purpose

Implement https://docs.aws.amazon.com/lambda/latest/dg/API_CreateEventSourceMapping.html and https://docs.aws.amazon.com/lambda/latest/dg/API_UpdateEventSourceMapping.html . Request contracts in CreateEventSourceMapping requires eventSourceArn, whereas UpdateEventSourceMapping requires UUID for that eventSourceArn. Just to reiterate, these operations are possible only on poll based event sources.

Because of this, there was a need to write an additional condition to fetch the UUID from cache for the specified eventSourceMapping Arn, so that the input requests can remain the same and should not require changes in input contract, thereby , keeping this operation to Upsert (instead of splitting in two viz, create and update)

As a result, even though using APIs, for a specific lambda function, the same event source ARN can be specified more than once, using Spinnaker, it can be done only once. At this point, i feel, its a good practice anyways and Spinnaker can enforce it, but there could be some edge cases for customers, which we should try to get a feedback from customers.

Point to note :

* Update operation allows only updating two properties viz batchsize and enabled. This limitation is imposed by APIs itself. For everything else, the mapping needs to be deleted and created again.


***Sample Request***


```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{   \
  "application":"DEMO-FUNCTION-2",   \
  "region":"us-west-2",   \
  "stack":"stack Goes here",   \
  "freeFormDetails":"",   \
  "credentials":"spinnaker-lambda",   \
  "batchsize": 1,   \
  "enabled": false,   \
  "eventsourcearn": "arn:aws:kinesis:us-west-2:123456789012:stream/guardduty-findings-stream"   \
  }' 'http://localhost:7002/awslambda/ops/upsertAwsLambdaEventMapping'
```

***Sample Request Body***

```
{
 "application":"DEMO-FUNCTION-2",
 "region":"us-west-2",
 "stack":"stack Goes here",
 "freeFormDetails":"",
 "credentials":"spinnaker-lambda",
 "batchsize": 1,
 "enabled": false,
 "eventsourcearn": "arn:aws:kinesis:us-west-2:123456789012:stream/guardduty-findings-stream"
 }
```

***Sample Response***

```
{
  "id": "f4a1d62f-df23-4ecd-adb9-d6c09763d1f8",
  "resourceUri": "/task/f4a1d62f-df23-4ecd-adb9-d6c09763d1f8"
}
```

***Sample Response from Task Controller***


```
{
  "history" : [ {
    "phase" : "ORCHESTRATION",
    "status" : "Initializing Orchestration Task..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Processing op: CreateLambdaEventSourceAtomicOperation"
  }, {
    "phase" : "CREATE_LAMBDA_FUNCTION_EVENT_MAPPING",
    "status" : "Initializing Creation of AWS Lambda Function Event Source Mapping..."
  }, {
    "phase" : "CREATE_LAMBDA_FUNCTION_EVENT_MAPPING",
    "status" : "Finished Creation of AWS Lambda Function Event Mapping Operation..."
  }, {
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  } ],
  "id" : "f4a1d62f-df23-4ecd-adb9-d6c09763d1f8",
  "ownerId" : "afbb6529-7f9a-4008-8a08-38b0862ce561@a-2hvzlfcrc0fk2",
  "resultObjects" : [ {
    "batchSize" : 1,
    "eventSourceArn" : "arn:aws:kinesis:us-west-2:123456789012:stream/guardduty-findings-stream",
    "functionArn" : "arn:aws:lambda:us-west-2:123456789012:function:DEMO-FUNCTION-2",
    "lastModified" : 1537392652024,
    "lastProcessingResult" : "No records processed",
    "sdkHttpMetadata" : {
      "httpHeaders" : {
        "Connection" : "keep-alive",
        "Content-Length" : "366",
        "Content-Type" : "application/json",
        "Date" : "Wed, 19 Sep 2018 21:30:52 GMT",
        "x-amzn-RequestId" : "48756323-bc53-11e8-9a26-7be5c7a7b5e5"
      },
      "httpStatusCode" : 202
    },
    "sdkResponseMetadata" : {
      "requestId" : "48756323-bc53-11e8-9a26-7be5c7a7b5e5"
    },
    "state" : "Creating",
    "stateTransitionReason" : "User action",
    "uuid" : "2e48314d-b72d-481b-bdc7-59df27757b9d"
  } ],
  "startTimeMs" : 1537392634528,
  "status" : {
    "complete" : true,
    "completed" : true,
    "failed" : false,
    "phase" : "ORCHESTRATION",
    "status" : "Orchestration completed."
  }
}
```
