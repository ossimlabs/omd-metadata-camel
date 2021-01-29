package io.ossim.unzipAndIngest

import groovy.json.JsonSlurper

import org.apache.camel.Processor
import org.apache.camel.Exchange


public class MosaicProcessor implements Processor {

	private Map mount
    private String urlPrefix
    private String urlSuffix

    private File logFile

	/**
     * Constructor.
     */
    public MosaicProcessor( ) {
        //this.mount = mount
        // this.urlPrefix = urlPrefix
        // this.urlSuffix = urlSuffix
        // this.logFile = new File("/${mount.bucket}/${mount.logFilePath}")
    }

    /**
     * Process to POST to omar stager the filename of the file corresponding
     * to the omd file in the exchange.
     *
     * @param exchange This exchange contains an omd file in which there should be a corresponding
     * image file for posting inside the same directory.
     */
    public void process(Exchange exchange) throws Exception {
        def map = exchange.in.getBody()
		// println "\n\n MOSAIC PROCESSOR HAS BEEN HITT \n\n"
        // println "Mosaic map = ${map}"
		def header = exchange.in.getHeaders()
		// println "mosiac header = ${header}"
		def body = exchange.in.getBody()
        def readyFile = new File(header.readyFile)
		// println "mosaic body = ${body}"

            def skySatDir = readyFile.getParent()
            def isPan = readyFile.getName().contains("panchromatic")
            def searchInclude = "*"
            def searchExclude = "*panchromatic*, *mosaic*"
            if (isPan){
                searchInclude = "*panchromatic*"
                searchExclude = "*mosaic*"
            }
            // def skySatId = skySatDir.find( /\d+_\d+_ssc\d+d\d+/ )
            // def mosaicReady = new File("${skySatDir}/${skySatId}_mosaic.ready")
            // def readyMosaic = exchange.getHeaders("MosaicReady")
            // println "moasic ready ${mosaicReady}"
            
            // convertWithChipper(readyFile)
    
                
                def images = new FileNameFinder().getFileNames(skySatDir, searchInclude + ".tif", searchExclude)
                def omds = new FileNameFinder().getFileNames(skySatDir, searchInclude + ".omd", searchExclude)
                def outImage =  readyFile.toString().replace(".ready", ".tif")

                createCombinedOMD(omds, outImage)
                println "\n\n_____CREATING MOSAIC "
                // logProcess(outImage)
                createMosaic(images, outImage)
                // url = this.urlPrefix + outImage + this.urlSuffix
                
                exchange.in.setHeader("postFilename", outImage)
                // exchange.in.setBody([filename: mosaicReady, body: outImage])
                // Post to stager when Mosaic is done
                // exchange.in.setHeader(Exchange.HTTP_URI, url)
                // exchange.in.setHeader("CamelHttpMethod", "POST")
                // // println exchange.in.getHeaders()
                // logHttp(url)
            
            
        }


   /**
     * Process to create a combined omd file from original omds for the mosaic.
     * 
     *
     * @omds a list of omd files to use to average the omd values
     * 
     * @outImage the output location to create the new omd file
     */
    private void createCombinedOMD(omds, outImage){
        def omdFile = new File(outImage.replace(".tif", ".omd"))
        def combinedMap = [:]
        
        // Loop through each omd to create combined map
        omds.each{ 
            def omd = new File(it) as String[]
            def map = 
    	            omd
                    .collectEntries { entry ->
                    def kvPair = entry.split(': ')
                        [(kvPair.first()): kvPair.last()]
                    }
            map.each {k,v -> 
                if (combinedMap.containsKey(k)){
                    if (v.isNumber()){
        	            combinedMap[k].push(v as Double)}
                }
                else {
                    if (v.isNumber()){
                        combinedMap[k] = [v as Double]
                    }
                    else {
                        combinedMap[k] = v
                    }
                }
            }
        }

        def omdbody = ""
        combinedMap.each{k, v ->
            // Check for numbers basically
            try {
                omdbody += "${k}: ${v.sum() / v.size()} \n"
            }  
            // if it's not a number assume it's text and don't need average 
            catch(e)
            {
                omdbody += "${k}: ${v} \n"
            }
        }    

        // Write out the combined omd averages
        if (omdFile.exists()) {
            println "Mosaic omd already exists "
            logOmdExists(omdFile)
        } 
        else {
            println "writing mosaic omdFile ${omdFile}"
            omdFile.withWriter { writer ->
                writer.write(omdbody)
            }
        }
    }

    // private void convertWithChipper(image){
    //     def outImage = new File(image.getAbsolutePath().replace(".omd", ".tif"))
    //     if (outImage.exists())
    //     {
    //         //println "${outImage} already exists."
    //         logExeError(outImage, "Image already exists", "Chipper Warning")
    //     }
    //     else {
    //         def tifCommand = [
    //             "ossim-chipper",
    //             "--op", "ortho",
    //             image.getAbsolutePath().replace(".omd",".ntf"), 
    //             outImage
    //         ]
    //         // println tifCommand

    //         // def output = executeCommand( tifCommand)	
    //         if (output.contains("Error"))
    //         {
    //             logExeError(outImage, output, "Chipper Error")
    //         }
    //         else{
    //             logExe(outImage, output, "Chipper Complete")
    //         }
    //     }
    // }

    private void createMosaic(images, outImage){
        println "Creating mosaic ${ outImage }..."
        
        def mosaicCommand = [
            "ossim-mosaic",
            "-m", "FEATHER",
            images.join( " " ),
            outImage
        ]
        println mosaicCommand
        def output = executeCommand( mosaicCommand)	
        if (output.contains("Error"))
        {
            logExeError(outImage, output, "Mosaic Error")
        }
        else{
            logExe(outImage, output, "Mosaic Complete")
        }


    }

    private String executeCommand( command ) {
        def process = command.execute()
        def standardOut = new StringBuffer()
        def standardError = new StringBuffer()
        process.waitForProcessOutput( standardOut, standardError )
        
        if (standardError)
        {

            return "Error - ${standardError}"
        }

        return "Completed Successfully - ${standardOut}"
    }



    private void logProcess(postFilePath) {
        Logger logger = new Logger("Merge", "PostProcessor",
                                   "Found omd file of image file to be posted",
                                   "File found for POST operation:",
                                   postFilePath.split('/').last(), ColorScheme.splitter,
                                   logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

    private void logOmdExists(filename) {
        Logger logger = new Logger("Omd", "PostProcessor",
                                   "Found duplicate omd file",
                                   "Filename:",
                                   filename, ColorScheme.splitter,
                                   logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }


    private void logExeError(filename, error, subhead) {
        String subtitle = "${subhead}: ${error}"
        Logger logger = new Logger("Processor", "UnzipProcessor", 
                                   subtitle, 
                                   "Output File failure:", 
                                   filename, ColorScheme.splitter, logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

    private void logExe(filename, output, subhead) {
        String subtitle = "${subhead}: ${output}"
        Logger logger = new Logger("Processor", "UnzipProcessor", 
                                   subtitle, 
                                   "Output File created:", 
                                   filename, ColorScheme.route, logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }  
}
