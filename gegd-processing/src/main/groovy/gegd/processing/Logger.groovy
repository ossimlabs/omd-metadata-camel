package gegd.processing
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

class Logger {
    public static final int printDividerLength = 100
    public static final String bodySpacer = "    "

    public static printCamelTitleScreen() {
        def titleLines = Camel.CAMEL_TITLE.split("\\n")
        def dividerLines = Camel.HUMP_DIVIDER.split("\\n")
        def camel = Camel.CAMEL_BIG.split("\\n")
        def dividerNoFace = Camel.HUMP_DIVIDER_NO_FACE.split("\\n")
        for (def l in dividerLines) {
            println ConsoleColors.WHITE_BOLD_BRIGHT + l
        }
        for (def l in titleLines) {
            println " "*12 + ConsoleColors.PURPLE_BOLD_BRIGHT + l
        }
        println ""
        for (def l in dividerNoFace) {
            println ConsoleColors.WHITE_BOLD_BRIGHT + l
        }
        // println ConsoleColors.WHITE + "${Camel.BASIC_DIVIDER}"*dividerLines[0].length()
        println ""
        for (def l in camel) {
            println ConsoleColors.PURPLE + l
        }
        for (def l in dividerLines) {
            println ConsoleColors.WHITE_BOLD_BRIGHT + l
        }
        println ""
    }

    public static printTitle(title, ColorScheme colorScheme) {
        def color = colorScheme.title
        println color + title
    }

    public static printSubtitle(subtitle, ColorScheme colorScheme) {
        def color = colorScheme.subtitle
        println color + subtitle
    }

    public static printBody(body, ColorScheme colorScheme, String colorOverride = null) {
        def color = colorScheme.body
        if (colorOverride != null)
            color = colorOverride
        def lines = body.split("\\n")
        for (String line in lines)
            println color + bodySpacer + line
        println ""
    }

    public static printDivider(title, name, ColorScheme colorScheme, boolean hasDate = false) {
        Date date = new Date();
        def dividerName = colorScheme.dividerName
        def divider = colorScheme.divider
        def dateString = hasDate ? "${date.toString()} " : ""
        def dLength = hasDate ? date.toString().length() : 0
        def titleLength = (title.length() + name.length() + 4 + dLength)

        println dividerName + "${title}" + ConsoleColors.WHITE + " [${name}] " + ConsoleColors.CYAN + dateString + divider + "*"*(printDividerLength - titleLength)
    }
}

public class Camel {
    public static final String BORDER = '/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7_/"7'

    public static final String HUMP_DIVIDER_NO_FACE = '     .-.     .-.     .-.     .-.     .-.     .-.     .-.     .-.    \n`._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.'

    public static final String HUMP_DIVIDER = '                                                             ,^-.__  \n                                                             /      \\\n     .-.     .-.     .-.     .-.     .-.     .-.     .-.    |  ;-\'\'\'\'\n`._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'   `._.\'  |     '
    public static final String BASIC_DIVIDER = '='

    // (60x21)
    public static final String CAMEL_BIG = '     &&&&&&&@                        &&&&&&                \n &&&&&&&&&&&&&                     &&&&&&&&&&               \n &&&&&&&&&&&&&&              &&&&&&&&&&&&&&&&&&             \n         &&&&&&          *&&&&&&&&&&&&&&&&&&&&&&&&&         \n         @&&&&&&        &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&      \n          &&&&&&&    %&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&    \n          &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&   \n            &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&  \n              &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&  \n                         &&&&&&&&&&&&&&&&&&&    &&&&&&&#&&  \n                          &&&&&&&&%             &&&&&&&& &  \n                          &&&& &&&&              &&&&&&&&   \n                          &&&.  &&&               &&&&*&&&  \n                          &&&,  &&&#              (&&&  &&&&\n                          &&&&  &&&&               &&&, ,&&&\n                          &&&&   &&&              #&&&   &&&\n                          .&&     &&              &&@    &&%\n                           &&     &&             &&.     && \n                          *&&     &&&           &&,      && \n                          &&&     &&&          &&&       && \n                       ,&&&&    &&&&&       &&&&&     &&&&& \n'

    // (36x6)
    // public static final String CAMEL_TITLE = "    _____                     _\n   / ____|                   | |\n  | |     __ _ _ __ ___   ___| |\n  | |    / _` | '_ ` _ \\ / _ \\ |\n  | |___| (_| | | | | | |  __/ |\n   \\_____\\__,_|_| |_| |_|\\___|_|\n"

    // (40x6)
    public static final String CAMEL_TITLE = '   ___                              _      \n  / __|   __ _    _ __     ___     | |      \n | (__   / _` |  | \'  \\   / -_)    | |     \n  \\___|  \\__,_|  |_|_|_|  \\___|   _|_|_     \n_|"""""|_|"""""|_|"""""|_|"""""|_|"""""|    \n"`-0-0-\'"`-0-0-\'"`-0-0-\'"`-0-0-\'"`-0-0-\''

}

public class ColorScheme {
    String title
    String subtitle
    String body
    String dividerName
    String divider

    public static ColorScheme route = new ColorScheme(ConsoleColors.CYAN_BOLD_BRIGHT, ConsoleColors.CYAN, ConsoleColors.GREEN, ConsoleColors.WHITE_BOLD, ConsoleColors.CYAN)
    public static ColorScheme aggregator = new ColorScheme(ConsoleColors.GREEN_BOLD_BRIGHT, ConsoleColors.GREEN, ConsoleColors.YELLOW, ConsoleColors.WHITE_BOLD, ConsoleColors.GREEN)
    public static ColorScheme splitter = new ColorScheme(ConsoleColors.PURPLE_BOLD_BRIGHT, ConsoleColors.PURPLE, ConsoleColors.CYAN, ConsoleColors.WHITE_BOLD, ConsoleColors.PURPLE)
    public static ColorScheme http = new ColorScheme(ConsoleColors.YELLOW_BOLD_BRIGHT, ConsoleColors.YELLOW, ConsoleColors.WHITE, ConsoleColors.WHITE_BOLD, ConsoleColors.YELLOW)
    public static ColorScheme error = new ColorScheme(ConsoleColors.RED_BOLD_BRIGHT, ConsoleColors.RED, ConsoleColors.RED, ConsoleColors.RED_BOLD, ConsoleColors.RED)

    /**
     * Constructor.
     */
    public ColorScheme(title, subtitle, body, dividerName, divider) {
        this.title = title
        this.subtitle = subtitle
        this.body = body
        this.dividerName = dividerName
        this.divider = divider
    }
}
