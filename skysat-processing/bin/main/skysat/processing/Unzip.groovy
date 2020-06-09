package skysat.processing

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import io.micronaut.context.annotation.Value
import javax.inject.Singleton
import org.apache.camel.builder.RouteBuilder

import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.apache.camel.dataformat.zipfile.ZipFileDataFormat;
import org.apache.camel.dataformat.zipfile.ZipSplitter;

import java.util.Iterator;

@Singleton
class Unzip extends RouteBuilder {
    Logger logger;

    // SQS queue notifying the PUT of a zip file in s3.
    @Value('${app.sqs.zipQueue}')
    String zipQueue

    // S3 bucket to place files.
    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    // Directory where unzipped files will be created.
    @Value('${app.s3.directory.unzipped}') 
    String unzippedDirectory

    // Directory where the zip file will be moved to in s3.
    // Also where route 2 will look for zip files.
    @Value('${app.s3.directory.tempZip}')
    String tempZipDirectory                  

    // Directory where to copy s3 zip file in the local pod.
    @Value('${app.local.directory.zip}')
    String localZipDirectory

    @Override
    public void configure() throws Exception 
    {
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        ZipFileDataFormat zipFile = new ZipFileDataFormat();
        zipFile.setUsingIterator(true);

        from("aws-sqs://${zipQueue}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
            .unmarshal().json()
            .process { exchange ->
                def routeName = "Copy zip file to prefixed directory"
                def jsonSlurper = new JsonSlurper().parseText(exchange.in.body.Message)
                def data = [
                    bucketName: jsonSlurper.Records[0].s3.bucket.name,
                    objectKey: jsonSlurper.Records[0].s3.object.key]

                List<String> objectKeyItems = data.objectKey.split("/")
                String objectKeyName = tempZipDirectory + "/" + objectKeyItems.last()

                ExchangeHandler.setS3Copy(exchange, data.objectKey, objectKeyName, s3BucketNameTo, s3BucketNameTo)

                logger = new Logger(routeName, exchange, zipQueue, "aws-sqs", s3BucketNameTo, "aws-s3", '')
                logger.logRoute()
            }
            .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")

        from("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&prefix=${tempZipDirectory}/")
            .process { exchange -> 
                def routeName = "Copy zip file to local file directory"
                String fullPath = exchange.getIn().getHeaders().CamelAwsS3Key;
                List<String> pathItems = fullPath.split("/")
                String filename = localZipDirectory + "/" + pathItems.last()

                ExchangeHandler.setFile(exchange, filename)
                
                logger = new Logger(routeName, exchange, s3BucketNameTo, "aws-s3", "/tmp/${filename}", "file", '')
                logger.logRoute()
            }
            .toD("file:///tmp/")

        from("file:///tmp/${localZipDirectory}/")
            .split(new ZipSplitter()).streaming()
            .process { exchange ->
                def routeName = "Unzip file into s3 bucket"
                def fullPath = exchange.getIn().getHeaders().CamelFileName;
                List<String> pathItems = fullPath.split("/")
                String filename = pathItems.last()
                filename = unzippedDirectory + filename

                ExchangeHandler.setS3(exchange, filename, filename, s3BucketNameTo, s3BucketNameTo, '')

                logger = new Logger(routeName, exchange, localZipDirectory, "file", s3BucketNameTo, "aws-s3", '')
                logger.logRoute()
            }
            .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true")
    }
}


