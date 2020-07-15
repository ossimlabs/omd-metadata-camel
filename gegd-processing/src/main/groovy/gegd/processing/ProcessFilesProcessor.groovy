package gegd.processing

import groovy.json.JsonSlurper
import org.apache.camel.Processor
import org.apache.camel.Exchange

public class ProcessFilesProcessor implements Processor {

    private Map mount
    private String[] dateKeys
    private def omdKeyMapList
    private String[] extensions

    private def ant = new AntBuilder()
    private String omdBody
    private String id
    private String processedDirectory

    public ProcessFilesProcessor(mount, dateKeys, omdKeyMapList, extensions) {
        this.mount = mount
        this.dateKeys = dateKeys
        this.omdKeyMapList = omdKeyMapList
        this.extensions = extensions
    }

    public void process(Exchange exchange) throws Exception {
        def json = new JsonSlurper().parseText(exchange.in.getBody(String.class))
        initialize(json)
        copyFilesAndSetOmdExchange(exchange, json)
    }

    private void initialize(json) {
        this.omdBody = getOmdFileBodyString(json, omdKeyMapList)
        this.id = findKeyValue(json, "id")
        this.processedDirectory = getProcessedDirectory(json)
    }

    private void copyFilesAndSetOmdExchange(exchange, json) {
        ArrayList<Map> omdFiles = new ArrayList<>()
        def filePath =  exchange.in.getHeaders().CamelFileAbsolutePath
        def relativePath = filePath.substring(0, filePath.lastIndexOf("/"))
        def scanner = ant.fileScanner {
            fileset(dir:"/${mount.bucket}/unzipped/") {
                include(name:"**/${this.id}*")
            }
        }

        int size = 0
        for (f in scanner) {
            size++
            def path = f.getAbsolutePath()
            def extension = path.substring(path.lastIndexOf(".") + 1, path.length())
            if (extensions.contains(extension)) {
                def omdFilename = path.substring(filePath.lastIndexOf("/"), path.lastIndexOf(".")) + ".omd"
                omdFiles.add([filename: "${this.processedDirectory}${omdFilename}", body: this.omdBody])
            }
        }

        logProcess(size, scanner, this.id, relativePath, "/${mount.bucket}/${this.processedDirectory}")
        logOmd(omdFiles)
    
        ant.move(todir:"/${mount.bucket}/${this.processedDirectory}/") {
            fileset(dir:"${relativePath}/") {
                include(name:"${this.id}*")
            }
        } 

        ant.chmod(perm:"777", type:"file") {
            fileset(dir:"/${mount.bucket}/${this.processedDirectory}/") {
                include(name:"${this.id}*")
            }

            dirset(dir:"/${mount.bucket}/${this.processedDirectory}") {}
        }

        exchange.in.setBody(omdFiles)
    }

    private String getProcessedDirectory(json) {
        String date = ''

        for (int i = 0; i < dateKeys.length; i++) {
            date = findKeyValue(json, dateKeys[i])
            if (date != null)
                break
        }

        date = date.substring(0, 10)
        return "${mount.archiveDirectory}/${date}/${this.id}"
    }

    private def getOmdFileBodyString(json, omdKeyMapList) {
        String fileBodyString = ''
        for ( map in omdKeyMapList ) {
            def pair = findKeyValuePairFromList(json, map.oldKeys)
            if (pair != null) {
                String keyVal = getChangedNamingCase(pair.value, map.values)
                fileBodyString += map.key + ": " + keyVal + "\n"
            }
        }
        return fileBodyString
    }

    private def getChangedNamingCase(oldValue, List<Map> valueMapList) {
        def value = valueMapList.find { it.oldValue == oldValue }?.newValue
        return value == null ? oldValue : value
    }

    private def findKeyValuePairFromList(json, keys) {
        for (key in keys) {
            def value = findKeyValue(json, key)
            if (value != null)
                return [key: key, value: value]
        }
        return null
    }

    private def findKeyValue(json, searchKey) {
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
    }

    private void logProcess(size, scanner, id, from, to) {
        Logger.printDivider("Processor", "ProcessFiles", ColorScheme.route)
        Logger.printTitle("Found metadata file for processing", ColorScheme.route)
        Logger.printSubtitle("Copying ${size} files with image_id: ${id} from ${from} to ${to}:", ColorScheme.route)

        for (f in scanner)
            Logger.printBody(f.getAbsolutePath().split("/").last(), ColorScheme.route, ConsoleColors.FILENAME)
    }

    private void logOmd(omdFiles) {
        Logger.printDivider("Processor", "ProcessFiles", ColorScheme.route)
        Logger.printTitle("Creating omd files", ColorScheme.route)
        Logger.printSubtitle("Omd files to create:", ColorScheme.route)

        for (f in omdFiles)
            Logger.printBody(f.filename, ColorScheme.route, ConsoleColors.FILENAME)
    }
}