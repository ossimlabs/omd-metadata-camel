package gegd.processing

import org.apache.camel.Processor
import org.apache.camel.Exchange

public class UnzipProcessor implements Processor {

    private Map mount

    public UnzipProcessor(mount) {
        this.mount = mount
    }

    public void process(Exchange exchange) throws Exception {
        def headersObj = exchange.in.getHeaders()
        def srcPath = headersObj.CamelFileAbsolutePath
        def prefixDir = srcPath.split("/").last()
        prefixDir = prefixDir.substring(0, prefixDir.lastIndexOf("."))
        println prefixDir  
        def ant = new AntBuilder()

        logProcess(srcPath)

        ant.unzip(  src:"${srcPath}",
                    dest:"/${mount.bucket}/unzipped/${prefixDir}/",
                    overwrite:"false" )

        def scanner = ant.fileScanner {
            fileset(dir:"/${mount.bucket}/unzipped/") {
                include(name:"**/*metadata.json")
            }
        }
        def prefix = "/${mount.bucket}/unzipped/"
        def donePath = ''
        for (f in scanner) {
            def path = f.getAbsolutePath()
            if (prefix.length() == path.lastIndexOf("/")) {
                println "SAME LENGTH!!!"
                donePath = "done"
            } else {
                donePath = path.substring(prefix.length(), path.lastIndexOf("/")) + "/done"
            }
            println "DONE PATH: " + donePath
            break;
        }

        exchange.in.setHeader("CamelFileName", "${donePath}")
        exchange.in.setBody("I'm done!")
    }

    private void logProcess(filename) {
        Logger.printDivider("Processor", "UnzipProcessor", ColorScheme.route)
        Logger.printTitle("Unzipping file for processing", ColorScheme.route)
        Logger.printSubtitle("File being unzipped:", ColorScheme.route)
        Logger.printBody(filename, ColorScheme.route, ConsoleColors.FILENAME)
    }
}