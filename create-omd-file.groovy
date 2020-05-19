import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import groovy.json.JsonSlurper
def data
def omdMap = [provider: "mission_id", address: "address"]

def sqs_queue_name = 'LidarData'
def s3_bucket_name_to = 'potree-test-data'
def s3_bucket_name_from = 'potree-test-data'
def processed_directory_name = 'processed'
def fileName = ''
def suffixToChange = '_metadata.json'
def dir = ''
def omdFormattedName = ''

beans {
    client = AmazonSQSClientBuilder.defaultClient()
}

// Grab files from s3 bucket updon SQS message and copy into processed directory.
from("aws-sqs://{{SQS_QUEUE_NAME}}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
    .unmarshal().json()
    .process { exchange ->

        def jsonSlurper = new JsonSlurper().parseText(exchange.in.body.Message)

        data = [
            bucketName: jsonSlurper.Records[0].s3.bucket.name,
            objectKey: jsonSlurper.Records[0].s3.object.key]

        List<String> objectKeyItems = data.objectKey.split("/")
        
        String objectKeyName = objectKeyItems.last()

        String body = exchange.getIn().getBody(String.class);
        exchange.getOut().setBody(body)
        exchange.getOut().setHeaders(exchange.getIn().getHeaders())

        // The key (file name) that will be copied from the S3_BUCKET_NAME_FROM
        exchange.in.setHeader("CamelAwsS3Key", "${data.objectKey}")
        // The bucket we are copying to
        exchange.in.setHeader("CamelAwsS3BucketDestinationName", "${s3_bucket_name_to}")
        // The key (file name) that will be used for the copied object
        exchange.in.setHeader("CamelAwsS3DestinationKey",  "${processed_directory_name}/${objectKeyName}")

        println "#"*80
        println "SQS message received. Copying ${data.objectKey} into ${processed_directory_name}"
        println "#"*80
    }
    .to("aws-s3://{{S3_BUCKET_NAME_FROM}}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")

// Get files from processed directory and copy into local pod.
from("aws-s3://{{S3_BUCKET_NAME_TO}}?useIAMCredentials=true&prefix=${processed_directory_name}/")
    .process { exchange ->
        Date date = new Date()
        String fileDate = date.format("yyyy-MM-dd")
        String body = exchange.getIn().getBody(String.class);

        def json = new JsonSlurper().parseText(body)
        def fileBodyString = ''

        int endSuffixIndex = data.objectKey.length() - suffixToChange.length()

        omdFormattedName = data.objectKey.substring(0, endSuffixIndex) + '.omd'

        omdMap.each{ entry ->
            String key = entry.key + '":'
            if (body.contains(key)) {
                fileBodyString += entry.value + ": " + json.properties[entry.key] + "\n"
            }
        }

        println "#"*80
        println "Copying " + omdFormattedName + " file to local pod at /tmp/" + processed_directory_name
        println "#"*80

        fileName = omdFormattedName
    
        exchange.getOut().setHeaders(exchange.getIn().getHeaders())
        exchange.getOut().setHeader(Exchange.FILE_NAME, simple("${processed_directory_name}/${omdFormattedName}"))
        exchange.getOut().setBody(fileBodyString)
    }
    .toD("file:///tmp/")

// Copy file back into the s3 bucket.
from("file:///tmp/processed/")
    .process { exchange ->
        println "#"*80
        println "Grabbed file: " + fileName
        println "Copying back into s3 bucket"
        println "#"*80
        // exchange.getIn().setHeader("CamelAwsS3ContentLength", simple("${in.header.CamelFileLength}"))
        exchange.getIn().setHeader("CamelAwsS3Key", "${fileName}");
    }
    .to("aws-s3://{{S3_BUCKET_NAME_TO}}?useIAMCredentials=true")



