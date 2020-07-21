package gegd.processing

import com.amazonaws.services.sqs.AmazonSQSClientBuilder

import io.micronaut.context.annotation.Value
import io.micronaut.context.annotation.Property

import javax.inject.Singleton

import org.apache.camel.Processor
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.Exchange
import org.apache.camel.Handler

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

            // 1. Grab zip files stored in the mounted buckets and ingest directory.
            // 2. Unzip the files into a unique, unzipped directory.
            // 3. Created a done file inside that same directory.
            from("file:///${mount.bucket}/${mount.ingestDirectory}/?noop=true&maxMessagesPerPoll=1")
                .filter(header("CamelFileName").endsWith(".zip"))
                .process(unzipProcessor)
                .to("file:///${mount.bucket}/${mount.unzipDirectory}/")

            // 1. Grab metadata.json files from the unzipped directory.
            // 2. Process the image files with the same id found in the metadata.
            // 3. Merge omd filenames and file bodies into a map and split for processing.
            // 3. Create an omd file in the processed directory.
            from("file:///${mount.bucket}/${mount.unzipDirectory}/?noop=true&maxMessagesPerPoll=1&recursive=true&doneFileName=done")
                .filter(header("CamelFileName").endsWith("metadata.json"))
                .process(processFilesProcessor)
                .split(method(MapSplitter.class))
                .process { exchange ->
                    def map = exchange.in.getBody(Map.class)
                    
                    exchange.in.setHeader("CamelFileName", map.filename)
                    exchange.in.setBody(map.body)
                }
                .to("file:///${mount.bucket}/?chmod=777&chmodDirectory=777")

            // Grab omd files found in the processed directory.
            // Send a POST to omar stager for the correspoinding image file.
            from("file:///${mount.bucket}/${mount.archiveDirectory}/?noop=true&maxMessagesPerPoll=1&recursive=true")
                .filter(header("CamelFileName").endsWith(".omd"))
                .process(postProcessor)
                .setBody(constant(null)) // Set the exchange body to null so the POST doesn't send the file body.
                .choice()
                    .when(header("CamelHttpMethod").contains("stop"))
                        .process { exchange ->
                            def filepath = exchange.in.getHeaders().CamelFileAbsolutePath
                            println exchange.in.getHeaders().CamelFileAbsolutePath + " already staged!\n"
                        }
                    .otherwise()
                        .doTry()
                            .to("http://oldhost")
                            .process { exchange ->
                                logHttpResponse(exchange.in.getBody(String.class))
                            }
                        .doCatch(org.apache.camel.http.common.HttpOperationFailedException.class)
                            .process { exchange ->
                                final Throwable ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class)
                                logHttpError(ex.getMessage())
                            }
                    .end()
        }
    }

    private void logHttpResponse(message) {
        Logger.printDivider("HTTP", "Response", ColorScheme.http)
        Logger.printTitle("http response from omar-stager", ColorScheme.http)
        Logger.printSubtitle("Response body:", ColorScheme.http)
        Logger.printBody(message, ColorScheme.http, ConsoleColors.WHITE)
    }

    private void logHttpError(message) {
        Logger.printDivider("ERROR", "HTTP", ColorScheme.error)
        Logger.printTitle("Error caught when sending POST to omar-stager", ColorScheme.error)
        Logger.printSubtitle("Error:", ColorScheme.error)
        Logger.printBody(message, ColorScheme.error)
    }
}

public class MapSplitter {
    @Handler
    public ArrayList<Map> processMessage(Exchange exchange) {
        ArrayList<Map> map = exchange.in.getBody(ArrayList.class)
        return map
    }
}