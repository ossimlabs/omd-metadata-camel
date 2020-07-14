package gegd.processing

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import io.micronaut.context.annotation.Value
import io.micronaut.context.annotation.Property
import javax.inject.Singleton
import io.micronaut.context.annotation.Prototype
import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.builder.ExchangeBuilder
import org.apache.camel.AggregationStrategy
import org.apache.camel.Exchange
import org.apache.camel.Handler
import org.apache.camel.CamelContext
import java.io.InputStream

import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import org.apache.camel.dataformat.zipfile.ZipFileDataFormat
import org.apache.camel.dataformat.zipfile.ZipSplitter

import java.util.Iterator;

@Singleton
class ProcessGegdFilesRoute extends RouteBuilder {            

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
        bindToRegistry('client', AmazonSQSClientBuilder.defaultClient())

        for (Map mount in mounts) {
            Processor processFilesProcessor = new ProcessFilesProcessor(mount, dateKeys, omdKeyMapList, extensions)
            Processor unzipProcessor = new UnzipProcessor(mount)
            Processor postProcessor = new PostProcessor(urlPrefix, urlSuffix, extensions)

            from("file:///${mount.bucket}/${mount.ingestDirectory}/?noop=true&maxMessagesPerPoll=1&delete=true")
                .process(unzipProcessor)
                .to("file:///${mount.bucket}/unzipped/")

            from("file:///${mount.bucket}/unzipped/?noop=true&maxMessagesPerPoll=1&recursive=true&doneFileName=done")
                .filter(header("CamelFileName").endsWith("metadata.json"))
                .process(processFilesProcessor)
                .split(method(MapSplitter.class))
                .process { exchange ->
                    def map = exchange.in.getBody(Map.class)
                    
                    exchange.in.setHeader("CamelFileName", map.filename)
                    exchange.in.setBody(map.body)
                }
                .to("file:///${mount.bucket}/?chmod=777&chmodDirectory=777")

            from("file:///${mount.bucket}/${mount.archiveDirectory}/?noop=true&maxMessagesPerPoll=1&recursive=true")
                .filter(header("CamelFileName").endsWith(".omd"))
                .process(postProcessor)
                .setBody(constant(null)) // Set the exchange body to null so the POST doesn't send the file body.
                .choice()
                    .when(header("CamelFileName").contains("stop"))
                        .to("log:info")
                    .otherwise()
                        .to("http://oldhost")
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