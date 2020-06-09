# SkySat Processing
Micronaut Deployment of Apache Camel for processing incoming SkySat data.

---

### Routes

1. Copy zip file into camel watched directory
2. Copy zip file to local pod directory *(temporary directory)*
3. Unzip file and copy contents back into s3
4. Extract metadata json file into extracted directory *(temporary directory)*
5. Generate processing instructions from json file and send to SQS
6. Pull and carry out instructions from SQS message body (sort files and create omd)

---

### Environment Variables

Variable     | Description
------------ | -----------
`app.sqs.extractedJsonQueue` | *SQS queue notifying the creation of json into s3.*
`app.sqs.instructionQueue` | *SQS queue messages with processing instructions.*
`app.sqs.zipQueue` | *SQS queue notifying the PUT of a zip file in s3.*
`app.s3.bucket.to` | *S3 bucket to place files.*
`app.s3.directory.tempZip` | *S3 directory where the zip file will be moved to in s3.*
`app.s3.directory.unzipped` | *S3 directory where unzipped files will be created.*
`app.s3.directory.extractedJson` | *S3 directory for extracted, metadata json files.*
`app.local.directory.zip` | *Local directory where to copy s3 zip file in the local pod.*

---
