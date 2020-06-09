package skysat.processing

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import io.micronaut.context.annotation.Value
import javax.inject.Singleton
import org.apache.camel.builder.RouteBuilder

import com.amazonaws.services.sqs.AmazonSQSClientBuilder

@Singleton
class SendInstructionsMessage extends RouteBuilder {
    Logger logger
    boolean exitFlag = false

    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    @Value('${app.sqs.instructionQueue}')
    String instructionQueue

    @Value('${app.s3.directory.unzipped}')
    String unzippedDirectory

    @Value('${app.s3.directory.extractedJson}')
    String extractedJsonDirectory

    @Value('${app.suffixes}')
    String[] suffixes

    def omdMap = [provider: "mission_id"]
    def omdProviderNamingCaseMap = [skysat: "SkySat", blacksky: "BlackSky"]
    def routeName = 'Send instructions for file processing to SQS'

    @Override
    public void configure() throws Exception 
    {
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        from("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&prefix=${extractedJsonDirectory}/")
            .process { exchange ->
                def message = getMessage(exchange)
                def key = exchange.getIn().getHeaders().CamelAwsS3Key
                List<String> objectKeyItems = exchange.getIn().getHeaders().CamelAwsS3Key.split("/")
                String objectKeyName = objectKeyItems.last()

                if (message != null) {
                    ExchangeHandler.setSQS(exchange, message.toString())
                    logger = new Logger(routeName, exchange, s3BucketNameTo, "aws-s3", instructionQueue, "aws-sqs", '')
                    logger.logRoute()
                } else {
                    String destKey = ExchangeHandler.getErrorLogKey(objectKeyName)
                    ExchangeHandler.setS3Copy(exchange, key, destKey, s3BucketNameTo, s3BucketNameTo)
                    exchange.getOut().setBody("exitflag")
                }
            }
            .choice()
                .when(body().contains("exitflag"))
                    .toD("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")
                .otherwise()
                    .to("aws-sqs://${instructionQueue}?amazonSQSClient=#client&defaultVisibilityTimeout=2")
    }

    private def getMessage(exchange) {
        String body = exchange.getIn().getBody(String.class)
        def json = null;
        try {
            json = new JsonSlurper().parseText(body)
        } catch (Exception ex) {
            ErrorLogger.logException(ex, "Error while parsing json file.", routeName, "getMessage")
            exitFlag = true;
            return null;
        }

        if (json.id == null) {
            ErrorLogger.logError("json file does not contain field [id]", routeName, "getMessage")
            exitFlag = true;
            return null;
        }

        String omdBody = getNewFileBodyString(json, body)
        String omdDestKey = getDestinationKey(json, ".omd")
        def message = []
        suffixes.eachWithIndex { suffix, index ->
            def key = unzippedDirectory + json.id + suffix
            def destKey = getDestinationKey(json, suffix)
            def val = [key: key, destKey: destKey, body: ""]
            message.add(val)
        }
        def val = [key: "omd", destKey: omdDestKey, body: omdBody]
        message.add(val)
        def messageAsJson = JsonOutput.toJson(message)
        return messageAsJson
    }

    def getDestinationKey(json, String suffix) {
        String filename = json.id + suffix;
        String acquisition_date = json.properties.acquired.replaceAll(":", "-");
        return "${acquisition_date}/${json.id}/${filename}"
    }

    def getNewFileBodyString(json, body) {
        String fileBodyString = ''
        omdMap.each{ entry ->
            String key = entry.key
            if (body.contains(key)) {
                String keyVal = getChangedNamingCase(json.properties[entry.key], omdProviderNamingCaseMap)
                fileBodyString += entry.value + ": " + keyVal + "\n"
            }
        }
        return fileBodyString
    }

    def getChangedNamingCase(key, Map caseMap) {
        if (caseMap.containsKey(key.toLowerCase()))
            return caseMap.get(key.toLowerCase())
        else
            return key
    }
}
