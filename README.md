# omd-metadata-camel

Camel routes for creating a `.omd` file from `.json` metadata files

#### Dependencies
- Openshift oc client: [v3.11.0](https://github.com/openshift/origin/releases/tag/v3.11.0 "Openshift oc client")
- Apache Camel K client [camel-k-client](https://github.com/apache/camel-k/releases "Apache camel-k-client")

#### S3 Property Variables
Found in `s3.properties` file

1. `SQS_QUEUE_NAME`: The name of the queue that will be monitored for incoming messages.
2. `S3_BUCKET_NAME`: The name of the bucket that the files will be copied from. 

#### To run in development mode
1. Log in to Openshift: `oc login`
2. Supply Openshift login information
3. Change projects: `oc project <YOUR_OPENSHIFT_PROJECT>`
4. Run and deploy the component: ```kamel run -d mvn:org.codehaus.groovy:groovy-json:2.5.9 -d mvn:org.codehaus.groovy:groovy-dateutil:2.5.9 -e SQS_QUEUE_NAME=<YOUR_QUEUE_NAME> -e S3_BUCKET_NAME=<YOUR_BUCKET_NAME> create-omd-file.groovy --property-file s3.properties --dev```
 
 Note: _The dependencies in the run command above are needed for the Groovy JsonSlurper and Date formatting in the DSL._
