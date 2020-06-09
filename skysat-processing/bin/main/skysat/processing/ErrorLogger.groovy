package skysat.processing

class ErrorLogger {

    def static printDividerLength = 100

    public static logLoggingError(message, method) {
        String title = "Log Error in method [${method}()]"
        String[] titleArray = title.split("(?<=\\G.{${printDividerLength}})")
        String body = "Message: ${message}"
        String[] bodyArray = body.split("(?<=\\G.{${printDividerLength}})")
        println "-"*printDividerLength
        for (String t in titleArray)
            println t
        for (String b in bodyArray)
            println b
        println "-"*printDividerLength
    }

    public static logError(message, route, method) {
        String title = "Error in route [${route}], method [${method}()]"
        String[] titleArray = title.split("(?<=\\G.{${printDividerLength}})")
        String body = "Message: ${message}"
        String[] bodyArray = body.split("(?<=\\G.{${printDividerLength}})")
        println "-"*printDividerLength
        for (String t in titleArray)
            println ConsoleColors.RED + t
        for (String b in bodyArray)
            println ConsoleColors.RED + b
        println "-"*printDividerLength
    }

    public static logException(Exception ex, message, route, method) {
        String title = "Error in route [${route}], method [${method}()]"
        String[] titleArray = title.split("(?<=\\G.{${printDividerLength}})")
        String body = "Message: ${message}"
        String[] bodyArray = body.split("(?<=\\G.{${printDividerLength}})")

        String error = ex.toString()
        String[] errorArray = error.split("(?<=\\G.{${printDividerLength}})")

        println "-"*printDividerLength
        for (String t in titleArray)
            println ConsoleColors.RED + t
        for (String b in bodyArray)
            println ConsoleColors.RED + b
        println "- "*(printDividerLength/2)
        for (String e in errorArray)
            println ConsoleColors.RED + e
        println "-"*printDividerLength
    }

    public static ErrorMessage getErrorMessage(title, filename, route, body) {
        return new ErrorMessage(title, filename, route, body)
    }
}

class ErrorMessage {
    String title
    String date
    String filename
    String route
    String body

    public ErrorMessage(title, filename, route, body) {
        Date date = new Date()
        String fileDate = date.format("yyyy-MM-dd-hh:mm")
        this.title = title + " ERROR"
        this.date = date
        this.filename = filename
        this.route = route
        this.body = body
    }
}