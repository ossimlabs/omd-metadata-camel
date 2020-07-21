package gegd.processing

import org.apache.camel.Processor
import org.apache.camel.Exchange

public class UnzipProcessor implements Processor {

    private Map mount
    private def ant = new AntBuilder()

    /**
     * Constructor.
     */
    public UnzipProcessor(mount) {
        this.mount = mount
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

        logProcess(srcPath)

        this.ant.unzip(  src:"${srcPath}",
                    dest:"/${mount.bucket}/${mount.unzipDirectory}/${prefixDir}/",
                    overwrite:"false" )

        def scanner = ant.fileScanner {
            fileset(dir:"/${mount.bucket}/${mount.unzipDirectory}/") {
                include(name:"**/*metadata.json")
            }
        }
        def prefix = "/${mount.bucket}/${mount.unzipDirectory}/"
        def donePath = getDoneFilePath(scanner, prefix)

        ant.delete(file:"${srcPath}") {}

        exchange.in.setHeader("CamelFileName", "${donePath}")
        exchange.in.setBody("I'm done!")
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

    private void logProcess(filename) {
        Logger.printDivider("Processor", "UnzipProcessor", ColorScheme.route)
        Logger.printTitle("Unzipping file for processing", ColorScheme.route)
        Logger.printSubtitle("File being unzipped:", ColorScheme.route)
        Logger.printBody(filename, ColorScheme.route, ConsoleColors.FILENAME)
    }
}