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
        def ant = new AntBuilder()
        def filePath =  exchange.in.getHeaders().CamelFileAbsolutePath
        def filepathNoExtension = filePath.substring(0, filePath.lastIndexOf("."))
        String url = ''
        String postFilePath = ''
        File hisFile = new File("${filepathNoExtension}.his")

        if (hisFile.exists()) {
            exchange.in.setHeader("CamelFileName", "stop")
            return
        }

        for (e in extensions) {
            postFilePath = "${filepathNoExtension}.${e}"
            File postFile = new File(postFilePath)
            url = postFile.exists() ? "${urlPrefix}${postFilePath}${urlSuffix}" : url
        }

        if (url != '')
            logProcess(postFilePath)

        exchange.in.setHeader(Exchange.HTTP_URI, url)
        exchange.in.setHeader("CamelHttpMethod", "POST")

        logHttp(url)
    }

    private void logHttp(url) {
        Logger.printDivider("HTTP", "POST", ColorScheme.http)
        Logger.printTitle("Sending https post to Omar Stager", ColorScheme.http)
        Logger.printSubtitle("POST URL:", ColorScheme.http)
        Logger.printBody(url, ColorScheme.http, ConsoleColors.WHITE)
    }

    private void logProcess(postFilePath) {
        Logger.printDivider("Merge", "PostProcessor", ColorScheme.splitter)
        Logger.printTitle("Found omd file, creating list of POST url's and files for posting", ColorScheme.splitter)
        Logger.printSubtitle("File found for POST operation:", ColorScheme.splitter)
        Logger.printBody(postFilePath.split('/').last(), ColorScheme.splitter, ConsoleColors.FILENAME)
    }
}