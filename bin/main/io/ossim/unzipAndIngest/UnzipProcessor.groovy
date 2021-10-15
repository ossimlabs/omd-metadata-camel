package io.ossim.unzipAndIngest

import org.apache.camel.Processor
import org.apache.camel.Exchange
import org.apache.tools.ant.BuildException

public class UnzipProcessor implements Processor {

    private Map mount
    private File logFile

    private def ant = new AntBuilder()

    /**
     * Constructor.
     */
    public UnzipProcessor(mount) {
        this.mount = mount
        this.logFile = new File("/${mount.bucket}/${mount.logFilePath}")
    }

    /**
     * Process for unzipping incoming gegd zip files into a unique, prefixed directory.
     * 
     * @param exchange The exchange that contains a zip file containing images for processing.
     */
    public void process(Exchange exchange) throws Exception {
        def headersObj = exchange.in.getHeaders()
        def srcPath = headersObj.CamelFileAbsolutePath
        def prefixDir = srcPath.split("/").last()
        prefixDir = prefixDir.substring(0, prefixDir.lastIndexOf("."))

        try {
            this.ant.unzip(  src:"${srcPath}",
                    dest:"/${mount.bucket}/${mount.unzipDirectory}/${prefixDir}/",
                    overwrite:"false" )
        }
        catch( BuildException ex ) {
            if( ex.exception instanceof IOException ) {
                this.logError(srcPath, ex.exception.message + "\n\nMoving file to failed-zips directory")
            }
            else {
                this.logError(srcPath, "Could not unzip file.\n\nMoving file to failed-zips directory")
            }

            ant.move(file:"${srcPath}", todir:"/${mount.bucket}/failed-zips/", overwrite:"false", granularity:"9223372036854") {}

            exchange.in.setHeader("CamelFileName", "zipError")
            exchange.in.setBody("zipError")
        }

        logProcess(srcPath)

        def scanner = ant.fileScanner {
            fileset(dir:"/${mount.bucket}/${mount.unzipDirectory}/${prefixDir}/") {
                include(name:"**/*metadata.json")
            }
        }
        def prefix = "/${mount.bucket}/${mount.unzipDirectory}/"
        def donePath = getDoneFilePath(scanner, prefix)

        ant.delete(file:"${srcPath}") {}

        exchange.in.setHeader("CamelFileName", "${donePath}")
        exchange.in.setBody("I'm done!")

        logDone(donePath)
    }

    /**
     * Get the path to place the done file in. This file will prevent 
     * the remaining camel routes from kicking off until the zip file is completely extracted.
     * 
     * @param scanner Contains the metadata.json file necessary for creating the done file path.
     * @return String that represents the path where the done file will be created.
     */
    private String getDoneFilePath(scanner, prefix) {
        for (f in scanner) {
            def path = f.getAbsolutePath()
            if (prefix.length() == path.lastIndexOf("/"))
                return "done"
            else
                return path.substring(prefix.length(), path.lastIndexOf("/")) + "/done"
        }
        return "badDoneFile"
    }

    private void logError(filename, error) {
        Logger logger = new Logger("ERROR", "HTTP",
                                                ("Error caught when trying to unzip " + filename),
                                                "Error:",
                                                error, ColorScheme.error, logFile, true)
        logger.log()
    }

    private void logProcess(filename) {
        Logger logger = new Logger("Processor", "UnzipProcessor", 
                                   "Unzipping file for processing", 
                                   "File being unzipped:", 
                                   filename, ColorScheme.route, logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

    private void logDone(filename) {
        String subtitle = "Create done file in same directory as metadata.json file"
        if (filename == 'badDoneFile')
            subtitle = "Creating bad done file. No metadata.json file was found"
        Logger logger = new Logger("Processor", "UnzipProcessor", 
                                   subtitle, 
                                   "Done file name:", 
                                   filename, ColorScheme.route, logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }
}