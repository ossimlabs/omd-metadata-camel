package skysat.processing
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

class Logger {
    def printDividerLength = 100
    def headers
    def routeName
    def fromPath
    def fromComponent
    def toPath
    def toComponent
    def toVerb

    public Logger(routeName, exchange, fromPath, fromComponent, toPath, toComponent, toVerb) {
        String exchangeString = new JsonBuilder(exchange.getIn().getHeaders()).toPrettyString()
        this.routeName = routeName
        this.headers =  new JsonSlurper().parseText(exchangeString)
        this.fromPath = fromPath
        this.fromComponent = fromComponent
        this.toPath = toPath
        this.toComponent = toComponent
        this.toVerb = toVerb
    }

    static void main(String[] args) {
        Logger logger = new Logger('Extract Json', '{"CamelAwsS3BucketDestinationName": "ben-skysat","CamelAwsS3BucketName": "ben-skysat","CamelAwsS3DestinationKey": "processed/20190904_111013_ssc2d3_0033_metadata.json","CamelAwsS3Key": "uploaded/20190904_111013_ssc2d3_0033_metadata.json","CamelAwsSqsAttributes": {},"CamelAwsSqsMD5OfBody": "31df456b31b6c383c1e5a5f3a7960a5b","CamelAwsSqsMessageAttributes": {},"CamelAwsSqsMessageId": "dc518135-f6b6-4cf9-ac9c-ed50a56fe8fb","CamelAwsSqsReceiptHandle": "AQEB95n7f7jRCQ28QMtQCYHuQapFqwRN7tB9it+QaNNjajfHn7MfSeDHJlcebpqSqw5E2mvr17/kMNtDD5BOAvLvUaT6Xcmxy4Vo1MeSFww0EwZbeoO77IP4SFuEw5TevmDKUJbWxaVKRwkU1uiA9w6zhyaDCnNV3peFYjD11CJSLFRCA3pRnOkUrbiS91TX/eWbLlupt66F3OP2wzGxA1V/rTPeQUFU0OKHPizskazIqv0cc3aVdM9zHWZ+eTRSyQK+4HSonKptLLH2BT3leemqgEro4iBxvhxI+xIrcC/UBDgqqwLyEONhTe1aMKJbaPRhsWtMl6qDoEj6VUEbucfziimM3ijxP7dbHPeCsbTOECe26DOFhqdnnKz6zx/UC96CtVqgF+qIOHT3xtSyLhHR/g=="}',"SkySatCamelQueue", "aws-sqs", "Ben-SkySat", "aws-s3", '')
        logger.logRoute()
    }

    public logRoute() {
        printTop()
        logFrom()
        logTo()
    }
    
    private void logFrom() {
        switch (fromComponent) {
            case "aws-sqs":
                logSQS("from", fromPath, fromComponent, '')
                break;
            case "aws-s3":
                logS3("from", fromPath, fromComponent, '')
                break;
            case "file":
                logFile("from", fromPath, fromComponent, '')
                break;
            default:
                ErrorLogger.logError("Invalid component string!", routeName, "logFrom")
                break; 
        }
    }

    private void logTo() {
        switch (toComponent) {
            case "aws-sqs":
                logSQS("to", toPath, toComponent, toVerb)
                break;
            case "aws-s3":
                logS3("to", toPath, toComponent, toVerb)
                break;
            case "file":
                logFile("to", toPath, toComponent, toVerb)
                break;
            default:
                ErrorLogger.logError("Invalid component string!", routeName, "logTo")
                break; 
        }
    }

    private logSQS(kind, path, component, toVerb) {
        toVerb = toVerb == '' ? 'Sending' : toVerb
        def verb = kind == 'from' ? 'Received' : toVerb
        def subject = 'SQS message'

        printTitle(kind, component, path)  
        printBody(kind, component, verb, subject, path)
    }

    private logS3(kind, path, component, toVerb) {
        toVerb = toVerb == '' ? 'Copying' : toVerb
        def bucketFrom = headers.CamelAwsS3BucketName
        def bucketTo = headers.CamelAwsS3BucketDestinationName
        def filename = headers.CamelAwsS3Key == null ? headers.CamelFileName : headers.CamelAwsS3Key

        def verb = kind == 'from' ? 'Found' : toVerb
        def subject = "file ${filename}"
        def location = kind == 'from' ? bucketFrom : bucketTo
        if (location == null)
            location = path;

        printTitle(kind, component, path)  
        printBody(kind, component, verb, subject, location)
    }

    private logFile(kind, path, component, toVerb) {
        toVerb = toVerb == '' ? 'Copying' : toVerb
        def filename = headers.CamelFileName
        def verb = kind == 'from' ? 'Found' : toVerb
        def subject = "file ${filename}"
        def location = path

        printTitle(kind, component, path)  
        printBody(kind, component, verb, subject, location)
    }

    private printTitle(kind, component, name) {
        String title = "${kind}: ${component} [${name}]"
        String[] titleArray = title.split("(?<=\\G.{${printDividerLength}})")

        for (String t in titleArray)
            println ConsoleColors.CYAN_BRIGHT + t
    }

    private printBody(kind, component, verb, subject, location) {
        String body = "${verb} ${subject} ${kind} ${location}"
        String[] bodyArray = body.split("(?<=\\G.{${printDividerLength - 4}})")
        for (String b in bodyArray)
            println ConsoleColors.GREEN + "    " + b
        println ""
    }

    private printTop() {
        String title = "ROUTE [${routeName}] "

        println title + "*"*(printDividerLength - title.length())
    }
}