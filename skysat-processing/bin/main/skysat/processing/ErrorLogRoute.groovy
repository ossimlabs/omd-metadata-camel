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
class ErrorLogRoute extends RouteBuilder {
    Logger logger;

    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    @Value('${app.sqs.errorQueue}')
    String errorQueue

    @Override
    public void configure() throws Exception 
    {
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        from("aws-sqs://${errorQueue}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
            .process { exchange ->
                def routeName = "Create error log file in s3"
                String body = exchange.getIn().getBody(String.class)
                def jsonSlurper = new JsonSlurper().parseText(body)
                String destKey = ExchangeHandler.getErrorLogKey(jsonSlurper.filename)

                ExchangeHandler.setS3(exchange, destKey, destKey, s3BucketNameTo, s3BucketNameTo, body)
                logger = new Logger(routeName, exchange, errorQueue, "aws-sqs", s3BucketNameTo, "aws-s3", 'Creating')
                logger.logRoute()
            }
            .toD("aws-s3://${s3BucketNameTo}?useIAMCredentials=true")
    }
}
