package skysat.processing

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import io.micronaut.context.annotation.Value
import javax.inject.Singleton
import org.apache.camel.builder.RouteBuilder

import com.amazonaws.services.sqs.AmazonSQSClientBuilder

@Singleton
class ExtractJson extends RouteBuilder {
    Logger logger;

    @Value('${app.sqs.queue}')
    String sqsQueueName

    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    @Value('${app.sqs.instructionQueue}')
    String instructionQueue

    @Value('${app.extractedJsonDirectory}')
    String extractedJsonDirectory

    @Value('${app.suffixes}')
    String[] suffixes

    @Override
    public void configure() throws Exception 
    {
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        from("aws-sqs://${sqsQueueName}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
            .unmarshal().json()
            .process { exchange ->
                def jsonSlurper = new JsonSlurper().parseText(exchange.in.body.Message)
                def data = [
                    bucketName: jsonSlurper.Records[0].s3.bucket.name,
                    objectKey: jsonSlurper.Records[0].s3.object.key]

                List<String> objectKeyItems = data.objectKey.split("/")
                String objectKeyName = objectKeyItems.last()

                ExchangeHandler.setS3Copy(exchange, data.objectKey, "${extractedJsonDirectory}/${objectKeyName}", s3BucketNameTo, s3BucketNameTo)

                logger = new Logger("Extract Json from unzipped directory", exchange, sqsQueueName, "aws-sqs", s3BucketNameTo, "aws-s3", '')
                logger.logRoute()
            }
            .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")
    }

    public String getImageDirectory(String key, String[] suffixes) {
        for (String suffix in suffixes) {
            if (key.contains(suffix))
                return key.substring(0, key.length() - suffix.length())
        }
        return 'lonely-files'
    }

    public String sqsMsgAttributeNames() {
        return String.format("%s,%s", "Attr1", "Attr2");
    }
}
