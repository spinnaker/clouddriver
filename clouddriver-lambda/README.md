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
         "account": "mylambda",
         "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
         "codeSize": 7011514,
         "description": "sample",
         "eventSourceMappings": [],
         "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctiontwo",
         "functionName": "mylambdafunctiontwo",
         "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctiontwo",
         "handler": "lambda_function.lambda_handler",
         "lastModified": "2019-03-29T15:52:33.054+0000",
         "layers": [],
         "memorySize": 512,
         "region": "us-west-2",
         "revisionId": "58cb0acc-4a20-4e57-b935-cc97ae1769fd",
         "revisions": {
             "58cb0acc-4a20-4e57-b935-cc97ae1769fd": "$LATEST",
             "ee17b471-d6e3-47a3-9e7b-8cae9b92c626": "2"
         },
         "role": "arn:aws:iam::<acctno>:role/service-role/test",
         "runtime": "python3.6",
         "timeout": 60,
         "tracingConfig": {
             "mode": "PassThrough"
         },
         "version": "$LATEST"
     },
     {
         "account": "mylambda",
         "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
         "codeSize": 7011514,
         "description": "sample",
         "eventSourceMappings": [],
         "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctionone",
         "functionName": "mylambdafunctionone",
         "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctionone",
         "handler": "lambda_function.lambda_handler",
         "lastModified": "2019-03-29T15:46:04.995+0000",
         "layers": [],
         "memorySize": 512,
         "region": "us-west-2",
         "revisionId": "73e5500a-3751-4073-adc0-877dfc3c720d",
         "revisions": {
             "1e280c63-1bcd-4840-92dc-bef5f1b46028": "1",
             "73e5500a-3751-4073-adc0-877dfc3c720d": "$LATEST"
         },
         "role": "arn:aws:iam::<acctno>:role/service-role/test",
         "runtime": "python3.6",
         "timeout": 68,
         "tracingConfig": {
             "mode": "PassThrough"
         },
         "version": "$LATEST"
     }
 ]`
```

### Purpose

Retrieves details corresponding to a single lambda function.

***Sample Request***

```
curl -X GET --header 'Accept: application/json'
'http://localhost:7002/functions?functionName=mylambdafunctionone&region=us-west-2&account=mylambda'
```

***Sample Response***

```
[
    {
        "account": "mylambda",
        "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
        "codeSize": 7011514,
        "description": "sample",
        "eventSourceMappings": [],
        "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctionone",
        "functionName": "mylambdafunctionone",
        "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctionone",
        "handler": "lambda_function.lambda_handler",
        "lastModified": "2019-03-29T15:46:04.995+0000",
        "layers": [],
        "memorySize": 512,
        "region": "us-west-2",
        "revisionId": "73e5500a-3751-4073-adc0-877dfc3c720d",
        "revisions": {
            "1e280c63-1bcd-4840-92dc-bef5f1b46028": "1",
            "73e5500a-3751-4073-adc0-877dfc3c720d": "$LATEST"
        },
        "role": "arn:aws:iam::481090335964:role/service-role/test",
        "runtime": "python3.6",
        "timeout": 68,
        "tracingConfig": {
            "mode": "PassThrough"
        },
        "version": "$LATEST"
    }
]

```
### Purpose

Create a lambda function.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/createLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "description": "sample",
    "credentials": "mylambda",
    "handler": "lambda_function.lambda_handler",
	  "s3bucket": "my_s3_bucket_name",
    "s3key": "my_s3_object_key",
    "memory": 512,
    "publish": "true",
    "role": "arn:aws:iam::<acctno.>:role/service-role/test",
    "runtime": "python3.6",
    "timeout": "60",
    "tags": [{
    	"key":"value"
    }
    	
    ]
}'
```

***Sample Response***

```
{
    "id": "c3bd961d-c951-423e-aad6-918f29e78ccb",
    "resourceUri": "/task/c3bd961d-c951-423e-aad6-918f29e78ccb"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/c3bd961d-c951-423e-aad6-918f29e78ccb. So, I'll have to navigate to
http://localhost:7002/task/c3bd961d-c951-423e-aad6-918f29e78ccb for orchestration details
```

### Purpose

Update lambda function configuration.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/updateLambdaFunctionConfiguration \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctionone",
    "description": "sample",
    "credentials": "mylambda",
    "handler": "lambda_function.lambda_handler",
    "memory": 512,
    "role": "arn:aws:iam::<acctno>:role/service-role/test",
    "runtime": "python3.6",
    "timeout": "68",
    "tags": [{
    	"key":"value"
    }
    	
    ]
}'
```
Note: I've changed the timeout from 60 to 68. Naviagate to the aws console to see
if that change is being reflected.

***Sample Response***

```
{
    "id": "bfdb1201-1c31-4a83-84bb-a807d69291fc",
    "resourceUri": "/task/bfdb1201-1c31-4a83-84bb-a807d69291fc"
}

You may navigate to 
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/bfdb1201-1c31-4a83-84bb-a807d69291fc. So, I'll have to navigate to
http://localhost:7002/task/bfdb1201-1c31-4a83-84bb-a807d69291fc for orchestration details
```

### Purpose

Delete a lambda function.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/deleteLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda"
}'
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

