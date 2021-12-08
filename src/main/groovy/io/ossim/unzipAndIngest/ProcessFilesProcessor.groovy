package io.ossim.unzipAndIngest

import groovy.json.JsonSlurper
import org.apache.camel.Processor
import org.apache.camel.Exchange

/**
 * Processor to move files based on a metadata.json file
 * and create an omd file for each matched file to be ingested.
 */
public class ProcessFilesProcessor implements Processor {

    private Map mount
    private String[] dateKeys
    private def omdKeyMapList
    private String[] extensions
    private File logFile

    private def ant = new AntBuilder()


    private String omdBody // File content for the omd file created from the metadata
    private String id // Image id gathered from the metadata, eg 20201029_054557_ssc6d1_0001
    private String processedDirectory // Final directory location for ingest files
    private String skySatId // Image id removing the scene id, eg 20201029_054557_ssc6d1    
    private Boolean isSkySat // Is this a skysat bucket for skysat specific stuff
    private Boolean isPan //is this panchromatic

    /**
     * Constructor.
     */
    public ProcessFilesProcessor(mount, dateKeys, omdKeyMapList, extensions) {
        this.mount = mount
        this.dateKeys = dateKeys
        this.omdKeyMapList = omdKeyMapList
        this.extensions = extensions
        this.logFile = new File("/${mount.bucket}/${mount.logFilePath}")
        this.isSkySat = mount.bucket.contains("skysat")
    }

    /**
     * Processes the incoming exchange. Moves files to processed directory and creates the omd file.
     *
     * @param exchange The exchange containing the metadata.json file passed into this process
     */
    public void process(Exchange exchange) throws Exception {
        //def json = new JsonSlurper().parseText(exchange.in.getBody(String.class))
        //initialize(json)
        moveFilesAndSetOmdExchange(exchange)
    }

    /**
     * Initialize metadata-dependent class variables.
     *
     * @param json Json object from the metadata.json file
     */
    private void initialize(json) {
        this.omdBody = getOmdFileBodyString(json, omdKeyMapList)
        this.id = findKeyValue(json, "id")
        this.processedDirectory = getProcessedDirectory(json)
    }

    /**
     * Create the processed directory based on the json in the metadata file.
     *
     * @param json Json from the metadata.json file
     * @return String that is the processed directory
     */

    private void setProcessedDirectory(filePath) {
        def dateStr = getDateStr(filePath)
        def start = filePath.lastIndexOf("/")+1
        def date = dateStr.substring(0, 4) + "/" + dateStr.substring(4, 6) + "/" + dateStr.substring(6, dateStr.length())
        def processedDirectory = "${mount.archiveDirectory}/${date}/${filePath.substring(start, filePath.indexOf("_", start))}"

        this.processedDirectory = processedDirectory
    }

    private String getDateStr(filePath) {
        def fileSubStr = filePath.substring(filePath.lastIndexOf("/")+1, filePath.length())
        def strArr = fileSubStr.split("-")
        def dateStr = strArr[2] // 20211019

        return dateStr
    }
    /**
     * Moves unzipped files into processed directory and creates needed omd files.
     *
     * @param exchange The exchange passed into this process
     * @param json Json object from the metadata.json file
     */
    private void moveFilesAndSetOmdExchange(exchange) {
        ArrayList<Map> omdFiles = new ArrayList<>()
        def path =  exchange.in.getHeaders().CamelFileAbsolutePath
        def relativePath = path.substring(0, path.lastIndexOf("/"))
        String fileName = path.substring(path.lastIndexOf("/")+1, path.length())
        setProcessedDirectory(path)

        def extension = path.substring(path.lastIndexOf(".") + 1, path.length())
        if (extensions.contains(extension)) {
            String prefix = path.substring(path.lastIndexOf("/"), path.lastIndexOf("."))
            String omdBody = getOmdFileBodyString(path.substring(path.lastIndexOf("/")+1, path.length()))
            def omdFilename = prefix + ".omd"
            isPan = path.contains("panchromatic")
            def postFilename = prefix + "." + extension
            postFilename = "/${mount.bucket}/${this.processedDirectory}${postFilename}"
            omdFiles.add([filename: "${this.processedDirectory}${omdFilename}", body: omdBody, postFilename: postFilename])

            createOMD(path, omdBody)
            convertWithChipper(path)
            exchange.in.setHeader("postFilename", postFilename)
        }else{
            exchange.in.setHeader("postFilename", "Do Not Process")
        }

        logProcess(fileName, relativePath, "/${mount.bucket}/${this.processedDirectory}")
        ant.move(todir:"/${mount.bucket}/${this.processedDirectory}/", overwrite:"false", granularity:"9223372036854") {
            fileset(dir:"${relativePath}/") {
                include(name:"${fileName}")
            }
        }

        ant.chmod(perm:"777", type:"file") {
            fileset(dir:"/${mount.bucket}/${this.processedDirectory}/") {
                include(name:"${fileName}")
            }
            dirset(dir:"/${mount.bucket}/${this.processedDirectory}") {}
        }

        ant.delete() {
            fileset(dir:"${relativePath}/") {
                include(name:"${fileName}")
            }
        }

        if (this.isSkySat){
            def skySatFilesByIdCount = new FileNameFinder().getFileNames("${relativePath}/", "${this.skySatId}*").size()

            println "\n\n_____SKYSAT ID ${this.skySatId} FILES COUNT_______ = ${skySatFilesByIdCount}"

            if (skySatFilesByIdCount == 0)
            {       
                println "\n\n_____CREATING MOSAIC READY"    
                def readyFile = new File("/${mount.bucket}/${this.processedDirectory}/${this.skySatId}_mosaic.ready")     
                if (isPan){
                    readyFile = new File("/${mount.bucket}/${this.processedDirectory}/${this.skySatId}_panchromatic_mosaic.ready")
                }
                // println "mosaic file ${readyFile}"
                // readyFile.createNewFile() 
                exchange.in.setHeader("MosaicReady", "true")
                exchange.in.setHeader("ReadyFile", readyFile.toString())
            }
            else{
                exchange.in.setHeader("MosaicReady", "false")
            }
        }        

        exchange.in.setBody(omdFiles)
    }

    private void createOMD(image, body){
        def omdFile = new File(image.replace(".ntf", ".omd"))
        logOmd(omdFile)
        if (omdFile.exists()) {
            println "omd already exists "
            logOmdExists(omdFile)
        } else {
            println "writing omdFile"
            omdFile.withWriter { writer ->
                writer.write(body)
            }
        }
    }

    private void convertWithChipper(image){
        if (this.isSkySat){
            def outImage = new File(image.replace(".ntf", ".tif"))
            if (outImage.exists())
            {
                //println "${outImage} already exists."
                logExeError(outImage, "Image already exists", "Chipper Warning")
            }
            else {
                def tifCommand = [
                    "ossim-chipper",
                    "--op", "ortho",
                    image, 
                    outImage
                ]
                // println tifCommand

                def output = executeCommand( tifCommand)	
                if (output.contains("Error"))
                {
                    logExeError(outImage, output, "Chipper Error")
                }
                else{
                    logExe(outImage, output, "Chipper Complete")
                }
            }
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


    /**
     * Create the string to populate the omd files relating to this set of images.
     *
     * @param json
     * @param omdKeyMapList
     * @return String to populate the omd files
     */
//    private String getOmdFileBodyString(json, omdKeyMapList) {
//        String fileBodyString = ''
//        for ( map in omdKeyMapList ) {
//            def pair = findKeyValuePairFromList(json, map.oldKeys)
//            if (pair != null) {
//                String keyVal = getChangedNamingCase(pair.value, map.values)
//                fileBodyString += map.key + ": " + keyVal + "\n"
//            }
//        }
//        return fileBodyString
//    }

    private String getOmdFileBodyString(fileName) {
        String fileBodyString = ''
        String id = fileName.split("_")[0]
        // String dateStr = getDateStr(fileName)
        //def date = dateStr.substring(0, 4) + "/" + dateStr.substring(4, 6) + "/" + dateStr.substring(6, dateStr.length())
        fileBodyString = "mission_id: " + id

        return fileBodyString
    }

    /**
     * Get the new metadata value name for the given value from valueMapList.
     *
     * @param oldValue The old metadata value expected to be changed.
     * @param valueMapList The list of maps, mapping old metadata values to their new, desired values.
     * @return String to replace the old metadata value.
     */
    private String getChangedNamingCase(oldValue, List<Map> valueMapList) {
        def value = valueMapList?.find { it.oldValue == oldValue }?.newValue
        return value == null ? oldValue : value
    }

    /**
     * Find the first key/value map contained in a json object from a list of keys.
     *
     * @param json The json object to search through.
     * @param keys The list of keys to find a potential match in the json object.
     * @return Map contained in the json object or null if no match was found.
     */
    private Map findKeyValuePairFromList(json, keys) {
        for (key in keys) {
            def value = findKeyValue(json, key)
            if (value != null)
                return [key: key, value: value]
        }
        return null
    }

    /**
     * Find the value of the first instance of a key in a json object.
     *
     * @param json The json object to search through.
     * @param searchKey The key to be searched for in the json object.
     * @return String of the value of the key found in the json object or null if the key doesn't exist.
     */
    private String findKeyValue(json, searchKey) {
        if (json.getClass() != org.apache.groovy.json.internal.LazyMap)
            return null
        if (json[searchKey] != null)
            return json[searchKey]

        def keys = json.keySet() as String[]
        for (def key in keys) {
            String value = findKeyValue(json[key], searchKey)
            if (value != null)
                return value
        }
        return null
    }

    private void logProcess(fileName, from, to) {
        String body = fileName
        Logger logger = new Logger("Processor", "ProcessFiles",
                                   "Found file for processing",
                                   "Copying: ${fileName} from ${from} to ${to}:",
                                   body, ColorScheme.route, logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

    // private void logOmd(omdFiles) {
    //     String body = ""
    //     for (f in omdFiles)
    //         body += f.filename + "\n"

    //     Logger logger = new Logger("Processor", "ProcessFiles",
    //                                "Creating omd files",
    //                                "Omd files to create:",
    //                                body, ColorScheme.route, logFile, true, ConsoleColors.FILENAME)

    //     logger.log()
    // }

    private void logOmd(omdFile) {

        Logger logger = new Logger("Processor", "ProcessFiles",
                                   "Creating omd file",
                                   "Omd file to create:",
                                   omdFile.toString(), ColorScheme.route, logFile, true, ConsoleColors.FILENAME)

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

    private void logOmdExists(filename) {
        Logger logger = new Logger("Omd", "PostProcessor",
                                   "Found duplicate omd file",
                                   "Filename:",
                                   filename, ColorScheme.splitter,
                                   logFile, true, ConsoleColors.FILENAME)
        logger.log()
    }

}