package io.ossim.unzipAndIngest

import com.amazonaws.services.sqs.AmazonSQSClientBuilder

import io.micronaut.context.annotation.Value
import io.micronaut.context.annotation.Property

import javax.inject.Singleton

import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.Exchange
import org.apache.camel.Handler
import org.apache.camel.routepolicy.quartz.CronScheduledRoutePolicy;

import groovy.json.JsonSlurper

@Singleton
class ProcessGegdFilesRoute extends RouteBuilder {

    @Value('${app.wfsBaseUrl}')
    String wfsBaseUrl

    @Value('${app.wfsPostPrefix}')
    String wfsPostPrefix

    @Value('${app.wfsFilenamePrefix}')
    String wfsFilenamePrefix

    @Value('${app.sqsQueueArn}')
    String sqsQueueArn

    @Value('${app.ingestAlertQueueArn}')
    String ingestAlertQueueArn

    @Value('${app.logging.logFile}')
    String logFilePath

    // Array of mounts. These are the volume mounts of this pod.
    // Each mount has a bucket name and an archive and ingest directory.
    @Property(name = "app.mounts")
    Map[] mounts

    // The prefix of the stager url post. (everything BEFORE the filename)
    @Value('${app.post.url.prefix}')
    String urlPrefix

    // The suffix of the stager url post. (everything AFTER the filename)
    @Value('${app.post.url.suffix}')
    String urlSuffix

    // Generic acquisition date keys that are contained in the json metadata
    // files of both BlackSky and SkySat data.
    @Value('${app.parsing.dateKeys}')
    String[] dateKeys

    // This map contains keys inside json metadata files that need to be reformatted.
    // example: { provider: mission_id }
    @Property(name = "app.parsing.omdKeyMapList")
    def omdKeyMapList

    @Value('${app.fileExtensions}')
    String[] extensions

    @Override
    public void configure() throws Exception
    {
        CronScheduledRoutePolicy startPolicy = new CronScheduledRoutePolicy();
        startPolicy.setRouteStartTime("* * 12-18 * * ?");

        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        println omdKeyMapList

        for (m in mounts) {
            doRoute(m)
        }

        from("aws-sqs://${sqsQueueArn}?amazonSQSClient=#client&delay=500&maxMessagesPerPoll=10&deleteAfterRead=true")
            .routePolicy(startPolicy).noAutoStartup()
            .process { exchange ->
                def jsonSqsMsg = new JsonSlurper().parseText(exchange.in.getBody(String.class))

                def data = [
                    eventName: jsonSqsMsg.Records[0].eventName,
                    bucketName: jsonSqsMsg.Records[0].s3.bucket.name,
                    objectKey: jsonSqsMsg.Records[0].s3.object.key
                ] as Map<String, String>

                def wfsFP = wfsFilenamePrefix == "empty" ? "" : wfsFilenamePrefix

                def prefix = wfsBaseUrl + wfsPostPrefix
                def suffix = '%27&version=1.1.0'
                def filename = "${wfsFP}/${data.bucketName}/${data.objectKey}"
                def url = prefix + filename + suffix;

                def responseText = new URL( url ).getText()

                def responseJson = new JsonSlurper().parseText(responseText)
                if (responseJson.totalFeatures == 0) {
                    println "-"*80
                    println "Image file found that has not been ingested: \nfilepath: " + filename
                    println "- "*40

                    exchange.in.setHeader("CamelFileName", filename)
                } else {
                    exchange.in.setHeader("CamelFileName", "success")
                }

                def path = "${data.bucketName}/${data.objectKey}"
                exchange.in.setBody(path)                
            }
            .choice()
                .when(header("CamelFileName").endsWith(".ntf"))
                    .process { exchange ->
                        println "SENDING MESSAGE TO ingestAlert QUEUE"
                    }
                    .to("aws-sqs://${ingestAlertQueueArn}")
                .otherwise()
                    .process { exchange ->
                    }
                .end()
    }

    private void doRoute(Map mount) {
        mount.logFilePath = this.logFilePath
        File logFile = new File("/${mount.bucket}/${mount.logFilePath}")

        Processor processFilesProcessor = new ProcessFilesProcessor(mount, dateKeys, omdKeyMapList, extensions)
        Processor unzipProcessor = new UnzipProcessor(mount)
        Processor postProcessor = new PostProcessor(mount, urlPrefix, urlSuffix)

        // 1. Grab zip files stored in the mounted buckets and ingest directory.
        // 2. Unzip the files into a unique, unzipped directory.
        // 3. Created a done file inside that same directory.
        from("file:///${mount.bucket}/${mount.ingestDirectory}/?maxMessagesPerPoll=1&noop=true&scheduler=quartz&scheduler.cron=*+*+4-10+?+*+*")
            .filter(header("CamelFileName").endsWith(".zip"))
            .process(unzipProcessor)
            .choice()
                .when(body().contains("zipError"))
                    .to("file:///${mount.bucket}/failed-zips/")
                .otherwise()
                    .to("file:///${mount.bucket}/${mount.unzipDirectory}/")
                    
        // 1. Grab metadata.json files from the unzipped directory.
        // 2. Process the image files with the same id found in the metadata.
        // 3. Merge omd filenames and file bodies into a map and split for processing.
        // 3. Create an omd file in the processed directory.
        // 4. Send post
        from("file:///${mount.bucket}/${mount.unzipDirectory}/?maxMessagesPerPoll=1&recursive=true&doneFileName=done&noop=true&scheduler=quartz&scheduler.cron=*+*+4-10+?+*+*")
            .filter(header("CamelFileName").endsWith("metadata.json"))
            .process(processFilesProcessor)
            .split(method(MapSplitter.class))
            .process(postProcessor)
            .setBody(constant(null))
            .doTry()
                .to("http://oldhost")
                .process { exchange ->
                    Logger logger = new Logger("HTTP", "Response",
                                                "http response from omar-stager",
                                                "Response body:",
                                                exchange.in.getBody(String.class), ColorScheme.http, logFile, true)
                    // logger.log()
                }
            .doCatch(org.apache.camel.http.common.HttpOperationFailedException.class)
                .process { exchange ->
                    final Throwable ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class)
                    Logger logger = new Logger("ERROR", "HTTP",
                                                "Error caught when sending POST to omar-stager",
                                                "Error:",
                                                ex.getMessage(), ColorScheme.error, logFile, true)
                    logger.log()
                }
    }
}

public class MapSplitter {
    @Handler
    public ArrayList<Map> processMessage(Exchange exchange) {
        ArrayList<Map> map = exchange.in.getBody(ArrayList.class)
        return map
    }
}