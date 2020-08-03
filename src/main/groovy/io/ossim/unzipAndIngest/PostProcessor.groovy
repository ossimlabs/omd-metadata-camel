package io.ossim.unzipAndIngest

import org.apache.camel.Processor
import org.apache.camel.Exchange

public class PostProcessor implements Processor {

    private Map mount
    private String urlPrefix
    private String urlSuffix

    private File logFile

    /**
     * Constructor.
     */
    public PostProcessor(mount, urlPrefix, urlSuffix) {
        this.mount = mount
        this.urlPrefix = urlPrefix
        this.urlSuffix = urlSuffix
        this.logFile = new File("/${mount.bucket}/${mount.logFilePath}")
    }

    /**
     * Process to POST to omar stager the filename of the file corresponding
     * to the omd file in the exchange.
     *
     * @param exchange This exchange contains an omd file in which there should be a corresponding
     * image file for posting inside the same directory.
     */
    public void process(Exchange exchange) throws Exception {
        def map = exchange.in.getBody(Map.class)

        File omdFile = new File("/${mount.bucket}/${map.filename}")
        String url = this.urlPrefix + map.postFilename + this.urlSuffix

        omdFile.withWriter { writer ->
            writer.write(map.body)
        }

        logProcess(map.postFilename)

        exchange.in.setHeader(Exchange.HTTP_URI, url)
        exchange.in.setHeader("CamelHttpMethod", "POST")

        logHttp(url)
    }

    private void logHttp(url) {
        Logger logger = new Logger("HTTP", "POST",
                                   "Sending https post to Omar Stager",
                                   "POST URL:",
                                   url, ColorScheme.http, logFile, true, ConsoleColors.WHITE)
        logger.log()
    }

    private void logProcess(postFilePath) {
        Logger logger = new Logger("Merge", "PostProcessor",
                                   "Found omd file of image file to be posted",
                                   "File found for POST operation:",
                                   postFilePath.split('/').last(), ColorScheme.splitter,
                                   logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }
}