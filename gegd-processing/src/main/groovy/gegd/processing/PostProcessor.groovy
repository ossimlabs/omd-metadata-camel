package gegd.processing

import org.apache.camel.Processor
import org.apache.camel.Exchange

public class PostProcessor implements Processor {

    private String urlPrefix
    private String urlSuffix
    private String[] extensions

    public PostProcessor(urlPrefix, urlSuffix, extensions) {
        this.urlPrefix = urlPrefix
        this.urlSuffix = urlSuffix
        this.extensions = extensions
    }

    public void process(Exchange exchange) throws Exception {
        ArrayList<Map> postMapList = new ArrayList<>()
        def filePathAndName =  exchange.in.getHeaders().CamelFileAbsolutePath
        def filePath = filePathAndName.substring(0, filePathAndName.lastIndexOf("/"))
        def filenameNoExtension = filePathAndName.substring(filePathAndName.lastIndexOf("/")+1, filePathAndName.lastIndexOf("."))
        def ant = new AntBuilder()
        def scanner =   ant.fileScanner {
                            fileset(dir:"${filePath}/") {
                                for (e in extensions)
                                    include(name:"${filenameNoExtension}.${e}")
                            }
                        }

        String url = ''
        for (f in scanner) {
            url = "${urlPrefix}${f.getAbsolutePath().toString()}${urlSuffix}"
            break
        }

        exchange.getOut().setHeader(Exchange.HTTP_URI, url)
        exchange.getOut().setHeader("CamelHttpMethod", "POST")

        logProcess(scanner)
        logHttp(url)
    }

    private void logHttp(url) {
        Logger.printDivider("HTTP", "POST", ColorScheme.http)
        Logger.printTitle("Sending https post to Omar Stager", ColorScheme.http)
        Logger.printSubtitle("POST URL:", ColorScheme.http)
        Logger.printBody(url, ColorScheme.http, ConsoleColors.WHITE)
    }

    private void logProcess(scanner) {
        Logger.printDivider("Merge", "PostProcessor", ColorScheme.splitter)
        Logger.printTitle("Found omd file, creating list of POST url's and files for posting", ColorScheme.splitter)
        Logger.printSubtitle("File found for POST operation:", ColorScheme.splitter)

        for (f in scanner) {
            Logger.printBody(f.getAbsolutePath().split("/").last(), ColorScheme.splitter, ConsoleColors.FILENAME)
            break
        }
    }
}