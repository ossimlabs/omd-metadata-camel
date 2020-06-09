package skysat.processing

import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.annotation.Async

import groovy.transform.CompileStatic
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.SimpleRegistry

import org.apache.camel.builder.RouteBuilder

import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

import io.micronaut.context.annotation.Value

import org.apache.camel.Exchange
import org.apache.camel.Handler
import org.apache.camel.Message

class Application {

    @Value('${app.sqs.queue}')
    String sqsQueueName

    @Value('${app.s3.bucket.to}')
    String s3BucketNameTo

    @Value('${app.sqs.instructionQueue}')
    String instructionQueue

    @Value('${app.suffixes}')
    String[] suffixes

    @Value('${app.uploadDirectory}')
    String uploadDirectory

    String imageDirectory = null 

    def omdMap = [provider: "mission_id"]
    def omdProviderNamingCaseMap = [skysat: "SkySat", blacksky: "BlackSky"]

    @EventListener
    @Async
    public void onStartup(ServerStartupEvent event) {
        SimpleRegistry registry = new SimpleRegistry()
        
        registry.bind('client', AmazonSQSClientBuilder.defaultClient())

        CamelContext context = new DefaultCamelContext(registry)

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                if (false) {
                from('timer:tick?period=3000')
                        .setBody().constant("Hello world from Micronaut K8S Camel")
                    .to('log:info')
                }
                else {
                from("aws-sqs://${sqsQueueName}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
                    .unmarshal().json()
                    .process { exchange ->
                        def jsonSlurper = new JsonSlurper().parseText(exchange.in.body.Message)
                        def data = [
                            bucketName: jsonSlurper.Records[0].s3.bucket.name,
                            objectKey: jsonSlurper.Records[0].s3.object.key]

                        List<String> objectKeyItems = data.objectKey.split("/")

                        String objectKeyName = objectKeyItems.last()
                        imageDirectory = getImageDirectory(objectKeyName, suffixes);
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody(body)
                        exchange.getOut().setHeaders(exchange.getIn().getHeaders())

                        exchange.in.setHeader("CamelAwsS3Key", "${data.objectKey}")
                        exchange.in.setHeader("CamelAwsS3BucketDestinationName", "${s3BucketNameTo}")
                        exchange.in.setHeader("CamelAwsS3DestinationKey",  "processed/${objectKeyName}")
                    }
                    .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")

                    from("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&prefix=processed/")
                        .process { exchange ->
                            def message = getMessage(exchange)

                            exchange.getOut().setHeaders(exchange.getIn().getHeaders())
                            exchange.getOut().setBody(message.toString())
                        }
                        .to("aws-sqs://${instructionQueue}?amazonSQSClient=#client&defaultVisibilityTimeout=2")

                    from("aws-sqs://${instructionQueue}?amazonSQSClient=#client&delay=1000&maxMessagesPerPoll=5")
                        .split(method(JsonArraySplitter.class))
                        .process { exchange ->
                            String body = exchange.getIn().getBody(String.class)
                            body = formatMessageToJsonParsableString(body)
                            def json = new JsonSlurper().parseText(body)
                            String key = json.key
                            String destKey = json.destKey
                            exchange.in.setHeader("CamelAwsS3DestinationKey",  "${destKey}")
                            exchange.in.setHeader("CamelAwsS3BucketDestinationName", "${s3BucketNameTo}")

                            if (key == "omd") {
                                exchange.in.setHeader("CamelAwsS3Key", "${destKey}")
                                exchange.in.setBody(json.body)
                            } else
                                exchange.in.setHeader("CamelAwsS3Key", "${key}")
                        }
                        .choice()
                            .when(body().contains("mission"))
                            .toD("aws-s3://${s3BucketNameTo}?useIAMCredentials=true")
                        .otherwise()
                            .to("aws-s3://${s3BucketNameTo}?useIAMCredentials=true&deleteAfterRead=false&operation=copyObject")
                        .end()
                }
            }
        });

        context.start();
    }

    private def getMessage(exchange) {
        String body = exchange.getIn().getBody(String.class)
        def json = new JsonSlurper().parseText(body)
        String omdBody = getNewFileBodyString(json, body)
        println "Hello!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
        String omdDestKey = getDestinationKey(json, ".omd")
        def message = []
        suffixes.eachWithIndex { suffix, index ->
            def key = uploadDirectory + json.id + suffix
            def destKey = getDestinationKey(json, suffix)
            def val = [key: key, destKey: destKey, body: ""]
            message.add(val)
        }
        def val = [key: "omd", destKey: omdDestKey, body: omdBody]
        message.add(val)
        def messageAsJson = JsonOutput.toJson(message)
        return messageAsJson
    }


    private String formatMessageToJsonParsableString(body) {
        body = body.replaceAll(/\{/, /\{\"/)
        body = body.replaceAll(/\}/, /\"\}/)
        body = body.replaceAll(/=/, /\":\"/)
        body = body.replaceAll(/,\ /, /\",\"/)
        return body
    }

    private String getInstructionJsonString(key, destKey, body) {
        return "{\"key\":\"${key}\",\"destKey\":\"${destKey}\",\"body\":\"${body}\"}"
    }

    def getDestinationKey(json, String suffix) {
        String filename = json.id + suffix;
        String acquisition_date = json.properties.acquired.replaceAll(":", "-");
        return "${acquisition_date}/${json.id}/${filename}"
    }

    public String getImageDirectory(String key, String[] suffixes) {
        for (String suffix in suffixes) {
            if (key.contains(suffix))
                return key.substring(0, key.length() - suffix.length())
        }
        return 'lonely-files'
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

    static void main(String[] args) {
        Micronaut.run(Application)
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
