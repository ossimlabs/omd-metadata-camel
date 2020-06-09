package skysat.processing

class ExchangeHandler {

    static String logRootDirectory = 'logs'
    static String errorLogDirectory = 'errors'

    public static void setFile(exchange, filename) {
        exchange.getOut().setHeaders(exchange.getIn().getHeaders())
        exchange.getOut().setHeader("CamelFileName", "${filename}")
        exchange.getOut().setBody(exchange.getIn().getBody())
    }

    public static void setSQS(exchange, message) {
        exchange.getOut().setHeaders(exchange.getIn().getHeaders())
        exchange.getOut().setBody(message)
    }

    public static void setS3Copy(exchange, key, destKey, bucketName, bucketDestName) {
        setS3(exchange, key, destKey, bucketName, bucketDestName, '')
    }

    public static void setS3(exchange, key, destKey, bucketName, bucketDestName, body) {
        exchange.getOut().setBody(exchange.getIn().getBody())
        exchange.getOut().setHeaders(exchange.getIn().getHeaders())
        exchange.getOut().setHeader("CamelAwsS3Key", key)
        exchange.getOut().setHeader("CamelAwsS3DestinationKey", destKey)
        exchange.getOut().setHeader("CamelAwsS3BucketName", bucketName)
        exchange.getOut().setHeader("CamelAwsS3BucketDestinationName", bucketDestName)
        if (body != '')
            exchange.getOut().setBody(body)
    }

    public static String getErrorLogKey(key) {
        Date date = new Date()
        String fileDate = date.format("yyyy-MM-dd")

        return "${logRootDirectory}/${errorLogDirectory}/${fileDate}/${key}"
    }
}