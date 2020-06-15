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

    @Value('${app.sqs.errorQueue}')
    String errorQueue

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

                if (!exitFlag) {
                    ExchangeHandler.setSQS(exchange, message.toString())
                    logger = new Logger(routeName, exchange, s3BucketNameTo, "aws-s3", instructionQueue, "aws-sqs", '')
                    logger.logRoute()
                } else {
                    String filename = objectKeyName + ".error.txt"
                    ErrorMessage errorMessage = ErrorLogger.getErrorMessage("JSON", filename, routeName, message)
                    String msg = JsonOutput.toJson(errorMessage).toString()
                    ExchangeHandler.setSQS(exchange, msg)
                }
            }
            .choice()
                .when(body().contains("ERROR"))
                    .to("aws-sqs://${errorQueue}?amazonSQSClient=#client&defaultVisibilityTimeout=2")
                .otherwise()
                    .to("aws-sqs://${instructionQueue}?amazonSQSClient=#client&defaultVisibilityTimeout=2")
    }

    private def getMessage(exchange) {
        String body = exchange.getIn().getBody(String.class)
        def json = null;
        try {
            json = new JsonSlurper().parseText(body)
        } catch (Exception ex) {
            String message = "Error while parsing json file."
            ErrorLogger.logException(ex, message, routeName, "getMessage")
            exitFlag = true;
            return message;
        }

        if (json.id == null) {
            String message = "json file does not contain field [id]"
            ErrorLogger.logError("json file does not contain field [id]", routeName, "getMessage")
            exitFlag = true;
            return message;
        }

        String omdBody = getNewFileBodyString(json, body)
        String omdDestKey = getDestinationKey(json, ".omd")
        def message = []
        suffixes.eachWithIndex { suffix, index ->
            def key = unzippedDirectory + "/" + json.id + suffix
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
        acquisition_date = acquisition_date.substring(0, 10)
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
