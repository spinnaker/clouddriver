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

***Sample Response***

```
{
    "id": "4c316ba9-7db8-4675-82d9-5adf118c541c",
    "resourceUri": "/task/4c316ba9-7db8-4675-82d9-5adf118c541c"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```

### Purpose

Invoke a lambda function.

***Sample Request***

```

curl -X POST \
  http://localhost:7002/aws/ops/invokeLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctionone",
    "credentials": "mylambda",
    "description": "sample",
    "Invocation-Type": "RequestResponse",
    "log-type": "Tail",
    "qualifier": "$LATEST",
    "outfile": "out.txt"
}'
```

***Sample Response***

```
{
    "id": "e4dfdfa1-0b3c-4980-a745-413eb9806332",
    "resourceUri": "/task/e4dfdfa1-0b3c-4980-a745-413eb9806332"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```


### Purpose

Update lambda function code.

***Sample Request***

```

curl -X POST \
  http://localhost:7002/aws/ops/updateLambdaFunctionCode \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda", 
	"s3Bucket": "my_s3_bucket_name",
    "s3Key": "my_s3_object_key",
    "publish": "true"
}'
```

***Sample Response***

```
{
    "id": "3a43157d-7f5d-4077-bc8d-8a21381eb6b7",
    "resourceUri": "/task/3a43157d-7f5d-4077-bc8d-8a21381eb6b7"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```

### Purpose

Upsert Event Mapping.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/upsertLambdaFunctionEventMapping \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda", 
    "batchsize" : "10",
    "majorFunctionVersion": "1",
    "enabled": "false",
    "eventSourceArn" : "arn:aws:kinesis:us-west-2:<myacctid>:stream/myteststream"
}'
```

***Sample Response***

```
{
    "id": "451b5171-7050-43b7-9176-483790e77bb6",
    "resourceUri": "/task/50540cf6-5859-44f6-9f13-9c4944386666"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```

### Purpose

Upsert Event Mapping.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/upsertLambdaFunctionEventMapping \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda", 
    "batchsize" : "10",
    "majorFunctionVersion": "1",
    "enabled": "false",
    "eventSourceArn" : "arn:aws:kinesis:us-west-2:<myacctid>:stream/myteststream"
}'
```

***Sample Response***

```
{
    "id": "451b5171-7050-43b7-9176-483790e77bb6",
    "resourceUri": "/task/50540cf6-5859-44f6-9f13-9c4944386666"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
It is important to capture the UUID from the orchestration details, 
If you plan to delete the event mapping later

{
    "history": [
        {
            "phase": "ORCHESTRATION",
            "status": "Initializing Orchestration Task..."
        },
        {
            "phase": "ORCHESTRATION",
            "status": "Processing op: UpsertLambdaEventSourceAtomicOperation"
        },
        {
            "phase": "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING",
            "status": "Initializing Creation of AWS Lambda Function Event Source Mapping..."
        },
        {
            "phase": "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING",
            "status": "Finished Creation of AWS Lambda Function Event Mapping Operation..."
        },
        {
            "phase": "ORCHESTRATION",
            "status": "Orchestration completed."
        }
    ],
    "id": "50540cf6-5859-44f6-9f13-9c4944386666",
    "ownerId": "831f24c7-a083-40fa-9b42-c106e6d5edb0@spin-clouddriver-d66d9f79b-tq8mw",
    "resultObjects": [
        {
            "batchSize": 10,
            "eventSourceArn": "arn:aws:kinesis:us-west-2:<acctid>:stream/mytest",
            "functionArn": "arn:aws:lambda:us-west-2:<acctid>:function:mylambdafunctiontwo",
            "lastModified": 1554382614013,
            "lastProcessingResult": "No records processed",
            "sdkHttpMetadata": {
                "httpHeaders": {
                    "Connection": "keep-alive",
                    "Content-Length": "352",
                    "Content-Type": "application/json",
                    "Date": "Thu, 04 Apr 2019 12:56:54 GMT",
                    "x-amzn-RequestId": "1ef75be6-56d9-11e9-8874-479d47ecf826"
                },
                "httpStatusCode": 202
            },
            "sdkResponseMetadata": {
                "requestId": "1ef75be6-56d9-11e9-8874-479d47ecf826"
            },
            "state": "Creating",
            "stateTransitionReason": "User action",
            "uuid": "4101b421-f0fb-4c89-8f99-6c2c153ec8d3"
        }
    ],
    "startTimeMs": 1554382613881,
    "status": {
        "complete": true,
        "completed": true,
        "failed": false,
        "phase": "ORCHESTRATION",
        "status": "Orchestration completed."
    }
}

In my case the UUID is 4101b421-f0fb-4c89-8f99-6c2c153ec8d3
```

### Purpose

Delete lambda function eventmapping

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/deleteLambdaFunctionEventMapping \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "application": "LAMBDA-PRINT-FUNCTION",
    "credentials": "mylambda",
    "UUID": "0ee2253a-737d-4863-9f19-84627785e85e"
}'
```

***Sample Response***

```
{
    "id": "0a01d76c-7942-46f0-810f-0f879f22e498",
    "resourceUri": "/task/0a01d76c-7942-46f0-810f-0f879f22e498"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/0a01d76c-7942-46f0-810f-0f879f22e498. So, I'll have to navigate to
http://localhost:7002/task/0a01d76c-7942-46f0-810f-0f879f22e498 for orchestration details
```
