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
}