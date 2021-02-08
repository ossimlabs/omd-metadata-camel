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

    }

    /**
     * Process to create a mosaic from tif files and
     * to merge omd files into one omd with averages.
     *
     * @param exchange This exchange contains an omd file in which there should be a corresponding
     * image file for processing inside the same directory.
     */
    public void process(Exchange exchange) throws Exception {
		// get the exchange header
        def header = exchange.in.getHeaders()
        def readyFile = new File(header.readyFile)

        def skySatDir = readyFile.getParent()
        def isPan = readyFile.getName().contains("panchromatic")
        def searchInclude = "*"
        def searchExclude = "*panchromatic*, *mosaic*"
        
        if (isPan){
            searchInclude = "*panchromatic*"
            searchExclude = "*mosaic*"
        }

        // gather the files for the mosaic 
        def images = new FileNameFinder().getFileNames(skySatDir, searchInclude + ".tif", searchExclude)
        def omds = new FileNameFinder().getFileNames(skySatDir, searchInclude + ".omd", searchExclude)
        def outImage =  readyFile.toString().replace(".ready", ".tif")

        // create the mosaic omd
        createCombinedOMD(omds, outImage)

        // create the mosaic
        createMosaic(images, outImage, readyFile)
        
        // Set the filename to the mosaic image
        exchange.in.setHeader("postFilename", outImage)          
    }


   /**
     * Process to create a combined omd file from original omds for the mosaic.
     * 
     *
     * @omds a List<String> of omd files to use to average the omd values
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

   /**
     * Process to create a mosaic from provided tif images.
     * 
     *
     * @images a List<String> of tif files to use for the mosaic
     * 
     * @outImage the output location to create the new omd file
     *
     * @doneFile The mosaic.ready file
     */
    private void createMosaic(images, outImage, doneFile){
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
            doneFile.delete()
        }
    }

   /**
     * Process to execute a command line call
     * 
     *
     * @command a list of the command and params
     */
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
