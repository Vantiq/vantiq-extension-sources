package io.vantiq.extsrc.assy.tasks

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.ArrayUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.common.FlowStyle
import org.gradle.api.Project

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import java.util.regex.Pattern

/**
 * Gradle task to build assembly specifications from Camel Kamelet definitions.
 */

@Slf4j
class AssemblyGen extends DefaultTask {
    public static final String KAMELET_GEN_BASE = "vantiqGeneratedResources"
    public static final String VANTIQ_SERVER_CONFIG = "vantiq://server.config"
    public static final String VANTIQ_SERVER_CONFIG_JSON = VANTIQ_SERVER_CONFIG + "?consumerOutputJson=true"
    
    public static final String KAMELETS_RESOURCE_FOLDER = "kamelets"
    public static final String KAMELETS_RESOURCE_PATH = KAMELETS_RESOURCE_FOLDER + "/"
    public static final String YAML_FILE_TYPE = ".yaml"
    public static final String YML_FILE_TYPE = ".yml"
    public static final String SOURCE_KAMELET_PREFIX = "-source.kamelet"
    public static final String SINK_KAMELET_PREFIX = "-sink.kamelet"
    public static final String SOURCE_KAMELET_YAML_SUFFIX = SOURCE_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SOURCE_KAMELET_YML_SUFFIX = SOURCE_KAMELET_PREFIX + YML_FILE_TYPE
    public static final String SINK_KAMELET_YAML_SUFFIX = SINK_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SINK_KAMELET_YML_SUFFIX = SINK_KAMELET_PREFIX + YML_FILE_TYPE
    
    public static final String KAMELET_SOURCE = "kamelet:source"
    public static final String KAMELET_SINK = "kamelet:sink"
    
    public static final String ASSEMBLY_PACKAGE_BASE = "io.vantiq.extsrc.camel.kamelets"
    public static final String PACKAGE_SEPARATOR = "."
    public static final String ROLE_SINK = "sink"
    public static final String ROLE_SOURCE = "source"
    
    // Vantiq directory names for project contents
    public static final String VANTIQ_DOCUMENTS_DIR = "documents"
    public static final String VANTIQ_PROJECTS_DIR = "projects"
    public static final String VANTIQ_SOURCES_DIR = "sources"
    public static final String VANTIQ_TYPES_DIR = "types"
    
    public final Project project
    
    @Inject
    AssemblyGen(Project project) {
        if (project == null) {
            throw new GradleException("AssemblyGen requires a non-null project.")
        }
        this.project = project
    }
    
    @OutputDirectory
    File getOutputDirectory() {
        if (!(project.property("buildDir") instanceof File)) {
            throw new GradleException("Internal Error: buildDir not a file: " + project.property("buildDir"))
        }
    
        File buildDir = (File) project.property("buildDir")
    
        assert buildDir != null
        return Path.of(buildDir.getAbsolutePath(), KAMELET_GEN_BASE).toFile()
    }
    
    /**
     * Overall processing for the generation of assemblies from kamelet declarations.
     *
     * @throws Exception
     */
    @TaskAction
    void generateAssemblies() throws Exception {
        File buildDir = (File) project.property("buildDir")
        assert buildDir != null
        if (!(buildDir.exists() && buildDir.isDirectory())) {
            throw new GradleException("Internal Error: BuildDir not present or not directory: " + buildDir)
        }
        Path generateBase = Paths.get(buildDir.getAbsolutePath(), KAMELET_GEN_BASE)
        if (!Files.exists(generateBase)) {
            log.info("Creating base dir: {}", generateBase)
            generateBase = Files.createDirectories(generateBase)
        }
        LoadSettings settings = LoadSettings.builder().build()
        Load load = new Load(settings)
    
        // From the gradle data, construct the list of resolved dependencies & build a class loader to use
        Configuration conf = project.getConfigurations().getByName("runtimeClasspath")
        Set<File> files = conf.resolve()
        log.info("File count from runtimeClasspath: {}", files.size())
        File[] jars = files.toArray(new File[0])
        ArrayList<URL> urls = new ArrayList<>(jars.length)
        for (File f: jars) {
            log.debug("Adding {} to list of URLs", f.toURI().toURL())
            urls.add(f.toURI().toURL())
        }
        URLClassLoader classloader =
                new URLClassLoader(urls.toArray(new URL[0]))
        // Find the URL that contains the "kamelets/" folder (which contains the kamelets toprocess)
        URL klUrl = classloader.getResource(KAMELETS_RESOURCE_FOLDER)
        log.debug("URL for kamelets: {}", klUrl)
        if (klUrl == null) {
            throw new GradleException("'kamelets' resource is null")
        }
        
        // Find path for jar file containing 'kamelets' folder
        String klString = klUrl.toString()
        klString = klString.substring(0, klString.indexOf("!/"))
        klString = klString.substring(9)
        def jff = new JarFile(klString)
        jff.withCloseable { jf ->
            try {
                String[] discards = new String[0]
                int kameletCount = 0
                // For each entry in this JAR file, determine if it's one we care about
                jf.entries().each { x ->
                    if (x.getName().startsWith(KAMELETS_RESOURCE_PATH)) {
                        String klet = x.getName().substring(KAMELETS_RESOURCE_PATH.length())
                        boolean isSink = klet.endsWith(SINK_KAMELET_YAML_SUFFIX) || klet.endsWith(SINK_KAMELET_YML_SUFFIX)
                        boolean isSource =
                            klet.endsWith(SOURCE_KAMELET_YAML_SUFFIX) || klet.endsWith(SOURCE_KAMELET_YML_SUFFIX)
                        if (isSink || isSource) {
                            // If it's a source or sink kamelet, we'll turn it into an assembly containing a source to
                            // process messages to or from that connection.
                            log.info("    name: {}", klet)
                            // First, read the YAML file that defines the kamelet.  From this, we'll extract the
                            // appropriate information to construct our assembly.
                            Map<String, Object> yamlMap = getYamlEnt(load, classloader, klet)
                            kameletCount += 1
                            log.debug("    \n<<<Result: parsed.size(): {}, parsed: {}>>>\n", yamlMap.size(),
                                yamlMap.get("spec"))

                            //noinspection unchecked
                            String title =
                                (String) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get("spec"))
                                    .get("definition")).get("title")
                            log.debug("Found spec for {}", title)

                            // Read any defined properties.  These will become properties for our assembly.
                            //noinspection unchecked
                            Map<String, Object> props =
                                (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get("spec"))
                                    .get("definition")).get("properties")
                            log.debug("    containing {} properties:", props.size())
                            props.forEach((k, v) -> {
                                //noinspection unchecked
                                log.debug("        name: {}, [type: {}, desc: {}]", k,
                                    ((Map<String, String>) v).get("type"),
                                    ((Map<String, String>) v).get("description"))
                            })
                            String kamName = klet.substring(0, klet.indexOf(".kamelet.yaml"))
                            String packageName = String.join(PACKAGE_SEPARATOR, ASSEMBLY_PACKAGE_BASE, kamName)

                            DumpSettings dumpSettings = DumpSettings.builder()
                                .setIndent(4)
                            // FLOW (default)serializes any embedded maps.
                            // This "recurses".
                                .setDefaultFlowStyle(FlowStyle.BLOCK)
                                .build()
                            Dump dumper = new Dump(dumpSettings)

                            // Now, extract the route template.  This will become our route document after we change
                            // kamelet:source or kamelet:sink to vantiq server URLs

                            //noinspection unchecked
                            Map<String, Object> template = (Map<String, Object>) ((Map<String, Object>) yamlMap
                                .get("spec")).get("template")
                            String originalTemplateString = dumper.dumpToString(template)
                            log.info("\n>>>   Original Template as Yaml:\n{}", originalTemplateString)

                            template = constructRouteText(template)

                            // Now, generate & populate results
                            Path thisKameletDirectory = Paths.get(generateBase.toAbsolutePath().toString(), kamName)
                            if (!Files.exists(thisKameletDirectory)) {
                                log.info("Creating kamelet-specific project directory: {}", thisKameletDirectory)
                                thisKameletDirectory = Files.createDirectories(thisKameletDirectory)
                            }
                            log.info("\nResults for Kamelet: {}", kamName)
                            log.info("\n>>>   Properties:")
                            props.forEach((name, val) -> {
                                //noinspection unchecked
                                log.info("    Name: {} :: [title: {}, type: {}, desc: {}]", name,
                                    ((Map<String, String>) val).get("title"),
                                    ((Map<String, String>) val).get("type"),
                                    ((Map<String, String>) val).get("description"))
                            })

                            LinkedHashMap<String, Object> revision = new LinkedHashMap<>()
                            String routeDocumentString = dumper.dumpToString(template)

                            log.info("\n>>>   Converted Template as Yaml:\n{}", routeDocumentString)
                            log.info("\nWriting project to: {}", thisKameletDirectory)

                            Path propFile = Paths.get(thisKameletDirectory.toAbsolutePath().toString(),
                                "props.json")
                            // FIXME: Convert this to a project file defining the assembly
                            Files.writeString(propFile, convertMapToJson(props))

                            writeRouteDocument(kamName, thisKameletDirectory, packageName, routeDocumentString)
                        }
                    } else {
                        discards = ArrayUtils.add(discards, x.getName())
                    }
                }

                if (discards.length > 0) {
                    log.debug("Discards")
                    for (String dumped : discards) {
                        log.debug("        dumped: {}", dumped)
                    }
                }
                project.getLogger().lifecycle("{} kamelets found & parsed.",
                    kameletCount)
                project.getLogger().lifecycle("    {} kamelets discarded (neither source nor sink as determined by " +
                    "naming conventions).",
                    discards.length)
            } catch (Exception e) {
                throw new GradleException("Error opening jar file: " + klString + " :: " +
                    e.getClass().getName() + " -- " + e.getMessage())
            }
        }
    }

    /** For the routeDocumentString provided, write it out as a Vantiq document as part of the kamelet's project.
     * *
     * @param kamName String name of the kamelet on which we're working
     * @param thisKameletDirectory Path the directory used for this kamelet assembly
     * @param packageName String package name we'll use for this assembly
     * @param routeDocumentString String the actual route document as extracted from the Kamelet
     * @return Path of document written
     */
    static Path writeRouteDocument(String kamName, Path thisKameletDirectory,
                                           String packageName, String routeDocumentString) {
        String docName = kamName + ".routes.yaml"
        writeVantiqEntity(VANTIQ_DOCUMENTS_DIR, thisKameletDirectory, packageName,
            docName, routeDocumentString, false)
    }

    static Path writeVantiqEntity(String entType, Path vantiqProjectDirectory, String packageName,
                                  String entName, String content, Boolean packageIsDirectory) {

        List<String> entDirectoryList = [vantiqProjectDirectory.toAbsolutePath().toString(), entType]
        if (packageIsDirectory) {
             entDirectoryList += packageName.split(Pattern.quote(PACKAGE_SEPARATOR)).toList()
        }
        Path entDir = Paths.get(*(entDirectoryList))
        if (!Files.exists(entDir)) {
            Files.createDirectories(entDir)
        }

        if (!packageIsDirectory) {
            entName = packageName + PACKAGE_SEPARATOR + entName
        }
        Path entFilePath = Paths.get(entDir.toAbsolutePath().toString(), entName)
        log.info("Should be building {}} as path: {}", entType, entFilePath.toString())
        return Files.writeString(entFilePath, content)
    }

    // Convert the route template into a reified route using the vantiq://server.config style endpoints
    Map<String, Object> constructRouteText(Map<String, Object> template) {
        processStep(template)
        return template
    }
    
    // For a particular step, convert it & its children
    void processStep(Map<String, Object> step) {
        log.debug("processStep() processing {}", step)
        for (Map.Entry<String, Object> ent: step.entrySet()) {
            String k = ent.getKey()
            Object v = ent.getValue()
            Map<String, Object> stepDef = null
            if (v instanceof Map) {
                //noinspection unchecked
                stepDef = (Map<String, Object>) v
            }
            log.debug("Found template key: {}, value: {}", k, v)
            if (k.equals("from") && stepDef != null) {
                if (stepDef.containsKey("uri") && stepDef.get("uri") instanceof String &&
                        ((String) stepDef.get("uri")).equals(KAMELET_SOURCE)) {
                    stepDef.put("uri", VANTIQ_SERVER_CONFIG_JSON)
                    log.debug("Replaced {} with{}", stepDef.get("uri"), VANTIQ_SERVER_CONFIG_JSON)
                }
                
                for (Map.Entry<String, Object> oneEntry: stepDef.entrySet()) {
                    String stepKey = oneEntry.getKey()
                    Object stepVal = oneEntry.getValue()
                
                    if (stepKey.equalsIgnoreCase("steps")
                                    || stepKey.equalsIgnoreCase("choice")
                                    || stepKey.equalsIgnoreCase("when")) {
                        if (stepVal instanceof Map) {
                            //noinspection unchecked
                            processStep((Map<String, Object>) stepVal)
                        } else if (stepVal instanceof List) {
                            //noinspection unchecked
                            processSteps((List<Map<String, Object>>) stepVal)
                        }
                    }
                }
            } else if (k.equals("to") && KAMELET_SINK.equals(v)) {
                log.debug("Replacing value of key {}:{} with \"vantiq://server.config\"", k, v)
                ent.setValue("vantiq://server.config")
            } else if (k.equals("to") && stepDef != null && stepDef.containsKey("uri")
                    && KAMELET_SINK.equals(stepDef.get("uri"))) {
                stepDef.put("uri", "vantiq://server.config")
    
            }
        }
    }
    
    // For a list of steps, convert each child
    void processSteps(List<Map<String, Object>> steps) {
        for (Map<String, Object> step: steps) {
            processStep(step)
        }
    }

    static String convertMapToJson(Map<String, Object> source) {
        ObjectMapper objectMapper = new ObjectMapper()
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(source)
        } catch (JsonProcessingException e) {
            e.printStackTrace()
        }
        return null
    }
    
    /**
     * Using the YAML loader, read the named resource from the project classloader (remember, we are in a Gradle task).
     *
     * Return as a Map.
     *
     * @param loader Load Yaml loader
     * @param projectClassLoader ClassLoader class loader from which to get the named entity
     * @param name String name of resource from which to extract the YAML file information
     * @return Map<String, Object> representing the route
     */
    static Map<String, Object> getYamlEnt(Load loader, ClassLoader projectClassLoader, String name) {
        String kameletToFetch = KAMELETS_RESOURCE_PATH + name
        InputStream kameletStream = projectClassLoader.getResourceAsStream(kameletToFetch)
    
        if (kameletStream == null) {
            throw new GradleException("Cannot read kamelet Stream: " + kameletToFetch)
        }
        Object raw = loader.loadFromInputStream(kameletStream)
        if (!(raw instanceof Map)) {
            throw new GradleException("Internal Error:  Expected Map from kamelStream, got " +
                                              kameletStream.getClass().getName())
        }
        //noinspection unchecked
        Map<String, Object> parsed = (Map<String, Object>) raw
        if (parsed.size() == 0) {
            throw new GradleException("Setup Conditions: No values found in kameletStream.")
        }
        return parsed
    }
}
