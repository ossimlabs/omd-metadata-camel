package skysat.processing

import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.micronaut.scheduling.annotation.Async

import groovy.transform.CompileStatic

import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.SimpleRegistry

class Application {

    Unzip unzip
    ExtractJson extractJson
    SendInstructionsMessage sendInstructionsMessage
    SortAndCreateOmd sortAndCreateOmd
    ErrorLogRoute errorLogRoute

    Application(Unzip unzip, 
                ExtractJson extractJson, 
                SendInstructionsMessage sendInstructionsMessage, 
                SortAndCreateOmd sortAndCreateOmd,
                ErrorLogRoute errorLogRoute)
    {
        this.unzip = unzip
        this.extractJson = extractJson
        this.sendInstructionsMessage = sendInstructionsMessage
        this.sortAndCreateOmd = sortAndCreateOmd
        this.errorLogRoute = errorLogRoute
    }

    @EventListener
    @Async
    public void onStartup(ServerStartupEvent event) {
        SimpleRegistry registry = new SimpleRegistry()
        CamelContext context = new DefaultCamelContext(registry)

        context.addRoutes(unzip)
        context.addRoutes(extractJson)
        context.addRoutes(sendInstructionsMessage)
        context.addRoutes(sortAndCreateOmd)
        context.addRoutes(errorLogRoute)
        context.start();
    }

    static void main(String[] args) {
        Micronaut.run(Application)
    }
}
