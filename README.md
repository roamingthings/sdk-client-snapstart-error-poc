# Proof of Concept to reproduce a potential Issue with Powertools for AWS Lambda

This proof-of-concept demonstrates an issue that occurs when a Lambda Function that's using SnapStart times out.
This also affects version 1.16.1 of Lambda Powertools for AWS Lambda (Java) (see https://github.com/aws-powertools/powertools-lambda-java/issues/1302)

For this reason the timeout of this function is set to a very short time (2s) leading the first invocation to time out.

After that the application is re-initialized. Due to the initialization of the DynamoDB client inside the idempotency
library subsequent calls will fail.

After deployment query the endpoint that has been created at least two times:

```
curl https://xxxxxxxx.execute-api.eu-central-1.amazonaws.com/Prod/test/
curl https://xxxxxxxx.execute-api.eu-central-1.amazonaws.com/Prod/test/
```

The first call times out and will fail with

```
{"message": "Internal server error"}
```

All subsequent queries will respond with
```
Exception: Failed to save in progress record to idempotency store. If you believe this is a Powertools for AWS Lambda (Java) bug, please open an issue.
```

When looking at the log of this invocation you will discover the cause for this exception:

```
software.amazon.awssdk.core.exception.SdkClientException: Unable to load credentials from system settings. Access key must be specified either via environment variable (AWS_ACCESS_KEY_ID) or system property (aws.accessKeyId).
```

See [log](log-events-viewer-result.csv) for details.

## Deployment

This project uses SAM for deployment:

```shell
sam build
sam deploy --guided
```
