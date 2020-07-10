package gegd.processing

import groovy.json.JsonSlurper
import org.apache.camel.Processor
import org.apache.camel.Exchange

public class ProcessFilesProcessor implements Processor {

    private Map mount
    private String[] dateKeys
    private def omdKeyMapList
    private String[] extensions

    public ProcessFilesProcessor(mount, dateKeys, omdKeyMapList, extensions) {
        this.mount = mount
        this.dateKeys = dateKeys
        this.omdKeyMapList = omdKeyMapList
        this.extensions = extensions
    }

    public void process(Exchange exchange) throws Exception {
        def json = new JsonSlurper().parseText(exchange.in.getBody(String.class))
        
        copyFilesAndSetOmdExchange(exchange, json)
    }

    private String getProcessedDirectory(json) {
        def id = findKeyValue(json, "id")
        def date = null

        for (int i = 0; i < dateKeys.length; i++) {
            date = findKeyValue(json, dateKeys[i])
            if (date != null)
                break
        }

        date = date.substring(0, 10)

        return "${mount.archiveDirectory}/${date}/${id}"
    }

    private void copyFilesAndSetOmdExchange(exchange, json) {
        ArrayList<Map> omdFiles = new ArrayList<>()
        def omdBody = getOmdFileBodyString(json, omdKeyMapList)
        def processedDirectory = getProcessedDirectory(json)
        def id = findKeyValue(json, "id")
        def ant = new AntBuilder()
        def relativePath = ''
        def scanner = ant.fileScanner {
            fileset(dir:"/${mount.bucket}/unzipped/") {
                include(name:"**/${id}*")
            }
        }

        int size = 0
        for (f in scanner) {
            size++
            def path = f.getAbsolutePath().toString()
            if (relativePath == '')
                relativePath = path.substring(0, path.lastIndexOf("/"))
            
            for (String extension in extensions) {
                if (path.endsWith(".${extension}")) {
                    def omdFilename = path.substring(path.lastIndexOf("/"), path.lastIndexOf(".")) + ".omd"
                    omdFiles.add([filename: "${processedDirectory}/${omdFilename}", body: omdBody])
                    break
                }
            }
        }

        logProcess(size, scanner, id, relativePath, "/${mount.bucket}/${processedDirectory}")
        
        ant.move(todir:"/${mount.bucket}/${processedDirectory}/") {
            fileset(dir:"${relativePath}/") {
                include(name:"${id}*")
            }
        }
        ant.chmod(perm:"777", type:"file") {
            fileset(dir:"/${mount.bucket}/${processedDirectory}/") {
                include(name:"${id}*")
            }

            dirset(dir:"/${mount.bucket}/${processedDirectory}") {}
        }

        exchange.in.setBody(omdFiles)
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
}