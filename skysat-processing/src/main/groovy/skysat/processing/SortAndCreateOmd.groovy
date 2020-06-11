package skysat.processing

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import io.micronaut.context.annotation.Value
import javax.inject.Singleton
import org.apache.camel.builder.RouteBuilder

import org.apache.camel.Exchange
import org.apache.camel.Handler
import org.apache.camel.Message

import com.amazonaws.services.sqs.AmazonSQSClientBuilder

@Singleton
class SortAndCreateOmd extends RouteBuilder {
    Logger logger;

    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    @Value('${app.sqs.instructionQueue}')
    String instructionQueue

    @Override
    public void configure() throws Exception 
    {
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        from("aws-sqs://${instructionQueue}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
            .split(method(JsonArraySplitter.class))
            .process { exchange ->
                String body = exchange.getIn().getBody(String.class)
                body = formatMessageToJsonParsableString(body)
                def json = new JsonSlurper().parseText(body)
                String key = json.key
                String destKey = json.destKey
                
                if (key == "omd") {
                    def routeName = "Create omd file from metadata"
                    ExchangeHandler.setS3(exchange, destKey, destKey, s3BucketNameTo, s3BucketNameTo, json.body)
                    logger = new Logger(routeName, exchange, instructionQueue, "aws-sqs", s3BucketNameTo, "aws-s3", 'Creating')
                } else {
                    def routeName = "Sort files by date and image_id"
                    ExchangeHandler.setS3Copy(exchange, key, destKey, s3BucketNameTo, s3BucketNameTo)
                    logger = new Logger(routeName, exchange, instructionQueue, "aws-sqs", s3BucketNameTo, "aws-s3", '')
                }

                logger.logRoute()
            }
            .choice()
                .when(header("CamelAwsS3DestinationKey").endsWith("omd"))
                    .toD("aws-s3://${s3BucketNameTo}?useIAMCredentials=true")
                .otherwise()
                    .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")
            .end()
    }

    private String formatMessageToJsonParsableString(body) {
        body = body.replaceAll(/\{/, /\{\"/)
        body = body.replaceAll(/\}/, /\"\}/)
        body = body.replaceAll(/=/, /\":\"/)
        body = body.replaceAll(/,\ /, /\",\"/)
        return body
    }
}

public class JsonArraySplitter {
    @Handler
    public String[] processMessage(Exchange exchange) {
        def messageList = [];
        def message = exchange.getIn();
        def msg = message.getBody(String.class);
        def json = new JsonSlurper().parseText(msg)

        for (def instruction in json)
            messageList.add(instruction.toString());

        return messageList;
    }
}
