package io.ossim.unzipAndIngest

import org.apache.camel.Processor
import org.apache.camel.Exchange

public class PostProcessor implements Processor {

    private Map mount
    private String urlPrefix
    private String urlSuffix

    private File logFile
    private Boolean isSkySat

    private def ant = new AntBuilder()

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
        def header = exchange.in.getHeaders()
            String url = this.urlPrefix + header.postFilename + this.urlSuffix
            logProcess(header.postFilename)
            exchange.in.setHeader(Exchange.HTTP_URI, url)
            exchange.in.setHeader("CamelHttpMethod", "POST")

            logHttp(url)
    }

    /**
     * Get the path to place the done file in. This file will prevent 
     * the remaining camel routes from kicking off until the zip file is completely extracted.
     * 
     * @param scanner Contains the metadata.json file necessary for creating the done file path.
     * @return String that represents the path where the done file will be created.
     */
    private String getReadyFilePath(path) {
            def prefix = "/${mount.bucket}/${mount.unzipDirectory}/"
            if (prefix.length() == path.lastIndexOf("/"))
                return "ready"
            else
                return path.substring(prefix.length(), path.lastIndexOf("/")) + "/ready"

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
                                   postFilePath, ColorScheme.splitter,
                                   logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

}