package io.vantiq.extsrc.assy.tasks

import groovy.json.JsonOutput
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
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
class AssemblyResourceGeneration extends DefaultTask {
    public static final String VANTIQ_SERVER_CONFIG = 'vantiq://server.config'
    public static final String VANTIQ_SERVER_CONFIG_JSON = VANTIQ_SERVER_CONFIG + '?consumerOutputJson=true'
    
    public static final String KAMELETS_RESOURCE_FOLDER = 'kamelets'
    public static final String KAMELETS_RESOURCE_PATH = KAMELETS_RESOURCE_FOLDER + '/'
    public static final String YAML_FILE_TYPE = '.yaml'
    public static final String YML_FILE_TYPE = '.yml'
    public static final String SOURCE_KAMELET_PREFIX = '-source.kamelet'
    public static final String SINK_KAMELET_PREFIX = '-sink.kamelet'
    public static final String SOURCE_KAMELET_YAML_SUFFIX = SOURCE_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SOURCE_KAMELET_YML_SUFFIX = SOURCE_KAMELET_PREFIX + YML_FILE_TYPE
    public static final String SINK_KAMELET_YAML_SUFFIX = SINK_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SINK_KAMELET_YML_SUFFIX = SINK_KAMELET_PREFIX + YML_FILE_TYPE
    public static final String PROPERTY_X_DESCRIPTORS = 'x-descriptors'
    public static final String DISPLAY_HIDE_VALUE = 'urn:alm:descriptor:com.tectonic.ui:password'
    // The following is used in an all-lower-case comparison, so it should be entirely lower case words
    public static final List<String> PASSWORD_LIKE = ['password']

    public static final String KAMELET_SOURCE = 'kamelet:source'
    public static final String KAMELET_SINK = 'kamelet:sink'
    
    public static final String ASSEMBLY_PACKAGE_BASE = 'io.vantiq.extsrc.camel.kamelets'
    public static final String PACKAGE_SEPARATOR = '.'
    
    // Vantiq directory names for project contents
    public final static String VANTIQ_SYSTEM_PREFIX = 'system.'
    public static final String VANTIQ_DOCUMENTS = 'documents'
    public static final String VANTIQ_PROJECTS = 'projects'
    public static final String VANTIQ_RULES = 'rules'
    public static final String VANTIQ_SERVICES = 'services'
    public static final String VANTIQ_SOURCES = 'sources'
    public static final String VANTIQ_TYPES = 'types'
    public static final String RULE_SINK_NAME_SUFFIX = '_svcToSrc'
    public static final String RULE_SOURCE_NAME_SUFFIX = '_srcToSvc'
    public static final String SERVICE_NAME_SUFFIX = '_service'
    public static final String SOURCE_NAME_SUFFIX = '_source'

    public static final String CAMEL_CONNECTOR_SOURCE_TYPE = 'CAMEL_SOURCE'
    public static final String PROPERTY_PLACEHOLDER_SUFFIX = '_placeholder'
    public static final String CONFIG_CAMEL_RUNTIME = 'camelRuntime'
    public static final String CONFIG_CAMEL_GENERAL = 'general'
    public static final String CAMEL_RUNTIME_APPNAME = 'appName'
    public static final String CAMEL_RUNTIME_PROPERTY_VALUES = 'propertyValues'
    public static final String CAMEL_RUNTIME_ROUTE_DOCUMENT = 'routeDocument'
    public static final String YAML_ROUTE_SUFFIX = '.routes.yaml'
    public static final String JSON_SUFFIX = '.json'
    public static final String VAIL_SUFFIX = '.vail'
    public static final String CAMEL_MESSAGE_SCHEMA = 'io.vantiq.extsrc.camelcomp.schema.base'

    public final Project project

    public static final String ASSEMBLY_RESOURCE_BASE_PROPERTY = 'generatedResourceBase'

    // Note that the following annotation 1) allows gradle to figure out what's up, and 2) will have gradle
    // create the directory (path)
    @OutputDirectory
    final File assemblyResourceBase = new File(project.buildDir,
        project.getExtensions()?.getExtraProperties()?.getAt(ASSEMBLY_RESOURCE_BASE_PROPERTY) as String)
    
    @Inject
    AssemblyResourceGeneration(Project project) {
        this.project = project
        if (project == null) {
            throw new GradleException('AssemblyGen requires a non-null project.')
        }
        // Since we depend only on dependencies (& this code), we'll just always re-run
        this.outputs.upToDateWhen { false }

        if (this.assemblyResourceBase == null) {
            throw new GradleException('AssemblyGen requires a non-null project extension property.')
        }
        log.info('Creating assembly resources under {}', this.assemblyResourceBase)
    }
    
    /**
     * Overall processing for the generation of assemblies from kamelet declarations.
     *
     * @throws Exception
     */
    @TaskAction
    void generateAssemblies() throws Exception {
        Path buildDir = project.getBuildDir()?.toPath()
        assert buildDir != null
        if (!(Files.exists(buildDir) && Files.isDirectory(buildDir))) {
            throw new GradleException('Internal Error: buildDir not present or not directory: ' + buildDir)
        }
        Path generateBase = assemblyResourceBase.toPath()
        LoadSettings settings = LoadSettings.builder().build()
        Load load = new Load(settings)
    
        // From the gradle data, construct the list of resolved dependencies & build a class loader to use
        Configuration conf = project.getConfigurations().getByName('runtimeClasspath')
        Set<File> files = conf.resolve()
        log.info('File count from runtimeClasspath: {}', files.size())
        File[] jars = files.toArray(new File[0])
        ArrayList<URL> urls = new ArrayList<>(jars.length)
        for (File f: jars) {
            log.debug('Adding {} to list of URLs', f.toURI().toURL())
            urls.add(f.toURI().toURL())
        }
        URLClassLoader classloader =
                new URLClassLoader(urls.toArray(new URL[0]) as URL[])
        // Find the URL that contains the 'kamelets/' folder (which contains the kamelets to process)
        URL klUrl = classloader.getResource(KAMELETS_RESOURCE_FOLDER)
        log.debug('URL for kamelets: {}', klUrl)
        if (klUrl == null) {
            throw new GradleException("kamelets' resource is null")
        }
        
        // Find path for jar file containing 'kamelets' folder
        String kameletJarFilename = klUrl.toString()
        kameletJarFilename = kameletJarFilename.substring(0, kameletJarFilename.indexOf('!/'))
        kameletJarFilename = kameletJarFilename.substring(KAMELETS_RESOURCE_PATH.length())

        processKameletsFromJar(kameletJarFilename, load, classloader, generateBase)
    }

    /**
     * Given a jar file, process Kamelets defined therein
     *
     * @param kameletJarFile String Name for the jar file containing the kamelets
     * @param yamlLoader Load YAML file loader
     * @param classLoader URLClassLoader classloader for files within the named jar file
     * @param outputRoot Path Root of location into which results will be placed
     */
    void processKameletsFromJar(String kameletJarFile, Load yamlLoader,
                                URLClassLoader classLoader, Path outputRoot) {
        def jff = new JarFile(kameletJarFile)
        jff.withCloseable { jf ->
            try {
                String[] discards = new String[0]
                int kameletCount = 0
                // For each entry in this JAR file, determine if it's one we care about
                jf.entries().each { jarEntry ->
                    if (jarEntry.getName().startsWith(KAMELETS_RESOURCE_PATH)) {
                        String kamelet = jarEntry.getName().substring(KAMELETS_RESOURCE_PATH.length())
                        boolean isSink = kamelet.endsWith(SINK_KAMELET_YAML_SUFFIX) || kamelet.endsWith(SINK_KAMELET_YML_SUFFIX)
                        boolean isSource =
                            kamelet.endsWith(SOURCE_KAMELET_YAML_SUFFIX) || kamelet.endsWith(SOURCE_KAMELET_YML_SUFFIX)
                        // ignore things neither sink nor source
                        if (isSink || isSource) {
                            kameletCount += 1
                            createAssemblyFromKamelet(kamelet, isSink, yamlLoader, classLoader, outputRoot)
                        }
                    } else {
                        discards = ArrayUtils.add(discards, jarEntry.getName())
                    }
                }

                if (log.isDebugEnabled() && discards.length > 0) {
                    log.debug('Discards')
                    for (String dumped : discards) {
                        log.debug('\tignored: {}', dumped)
                    }
                }
                project.getLogger().lifecycle('{} kamelets found & parsed.',
                    kameletCount)
                project.getLogger().lifecycle('    {} kamelets ignored\n      (neither source nor sink as ' +
                    'determined by naming conventions).', discards.length)
            } catch (Exception e) {
                throw new GradleException('Error opening jar file: ' + kameletJarFile + ' :: ' +
                    e.getClass().getName() + ' -- ' + e.getMessage())
            }
        }
    }

    /**
     * Given a single, named kamelet, construct a Vantiq assembly to perform our processing
     *
     * @param kameletDefinition String name of kamelet to be processed
     * @param isSink Boolean whether this is a SINK (vs. SOURCE -- others are ignored by the caller)
     * @param yamlLoader Load loader for YAML files in the kamelet
     * @param classLoader URLClassLoader loader to use to fetch the Kamelet definition
     * @param outputRoot Path location into which to save the Assembly definition
     */
    void createAssemblyFromKamelet(String kameletDefinition, Boolean isSink, Load yamlLoader, URLClassLoader classLoader,
                                   Path outputRoot) {
        // If it's a source or sink kamelet, we'll turn it into an assembly containing a source to
        // process messages to or from that connection.
        log.info('    Processing kamelet name: {}', kameletDefinition)
        // First, read the YAML file that defines the kamelet.  From this, we'll extract the
        // appropriate information to construct our assembly.
        Map<String, Object> yamlMap = getYamlEnt(yamlLoader, classLoader, kameletDefinition)
        log.debug('    \n<<<Result: parsed.size(): {}, parsed: {}>>>\n', yamlMap.size(),
            yamlMap.get('spec'))

        //noinspection unchecked
        String title =
            (String) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
                .get('definition')).get('title')
        log.info('Found spec for {}', title)

        // Read any defined properties.  These will become properties for our assembly.
        //noinspection unchecked
        Map<String, Object> props =
            (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
                .get('definition')).get('properties')
        log.debug('    containing {} properties:', props.size())
        props.forEach((k, v) -> {
            //noinspection unchecked
            log.debug('        name: {}, [type: {}, desc: {}]', k,
                ((Map<String, String>) v).get('type'),
                ((Map<String, String>) v).get('description'))
        })
        String kamName = kameletDefinition.substring(0, kameletDefinition.indexOf('.kamelet.yaml'))
        kamName = kamName.replace('-', '_')
        String packageName = String.join(PACKAGE_SEPARATOR, ASSEMBLY_PACKAGE_BASE, kamName)

        DumpSettings dumpSettings = DumpSettings.builder()
            .setIndent(4)
        // FLOW (default)serializes any embedded maps.
        // This will recurse.
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .build()
        Dump dumper = new Dump(dumpSettings)

        // Now, extract the route template.  This will become our route document after we change
        // kamelet:source or kamelet:sink to vantiq server URLs

        //noinspection unchecked
        Map<String, Object> template = (Map<String, Object>) ((Map<String, Object>) yamlMap
            .get('spec')).get('template')
        String originalTemplateString = dumper.dumpToString(template)
        log.info('\n>>>   Original Template as Yaml:\n{}', originalTemplateString)

        template = constructRouteText(template)

        // Now, generate & populate results
        Path assemblyRoot = Paths.get(outputRoot.toAbsolutePath().toString(), kamName)
        if (!Files.exists(assemblyRoot)) {
            log.info('Creating kamelet-specific project directory: {}', assemblyRoot)
            assemblyRoot = Files.createDirectories(assemblyRoot)
        }
        log.info('\nResults for Kamelet: {}', kamName)
        log.info('\n>>>   Properties:')
        props.forEach((name, val) -> {
            //noinspection unchecked
            log.info('    Name: {} :: [title: {}, type: {}, desc: {}]', name,
                ((Map<String, String>) val).get('title'),
                ((Map<String, String>) val).get('type'),
                ((Map<String, String>) val).get('description'))
        })

        String routeDocumentString = dumper.dumpToString(template)

        log.info('\n>>>   Converted Template as Yaml:\n{}', routeDocumentString)
        log.info('\nWriting project to: {}', assemblyRoot)

        Map routeDoc = writeRouteDocument(assemblyRoot, packageName, kamName, routeDocumentString)
        Map sourceDef = addSourceDefinition(assemblyRoot, packageName, kamName,
            routeDoc.path.fileName.toString(), props)
        log.info('Created sourceDef: {}', sourceDef)
        Map serviceDef = addServiceDefinition(assemblyRoot, packageName, kamName, isSink)
        log.info('Created serviceDef: {}', sourceDef)

        Map ruleDef = addRoutingRule(assemblyRoot, packageName, kamName,
            serviceDef.vailName as String,
            sourceDef.vailName as String, isSink)
        log.info('Created rule def: {}', ruleDef)
        List<String> componentList = [routeDoc.reference as String,
                                      sourceDef.reference as String,
                                      serviceDef.reference as String]
        if (ruleDef) {
            componentList << (ruleDef.reference as String)
        }
        log.info("Launching project def: addProjDef({}, {}, {}, {}, {}, {}",
            assemblyRoot, packageName, kamName, title, props, componentList)
        //noinspection GroovyUnusedAssignment       // Useful for debugging...
        Map projectDef = addProjectDefinition(assemblyRoot, packageName, kamName,
            title, props, componentList)
    }

    /**
     * Add service definition
     *
     * @param kameletAssemblyDir Path location of the base location for the project artifacts
     * @param packageName String name of the package to which this service will belong
     * @param kamName String name of the kamelet which will be used to construct the service name
     * @param isSink Boolean representing whether this service is for a sink or source kamelet/assembly
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    static Map<String, Object> addServiceDefinition(Path kameletAssemblyDir, String packageName, String kamName,
                                                    Boolean isSink) {
        def plainName = kamName + SERVICE_NAME_SUFFIX
        def name = packageName + PACKAGE_SEPARATOR + plainName
        def service = [
            active: true,
            ars_relationships: [],
            description: 'Service connecting source ' + packageName + PACKAGE_SEPARATOR + kamName +
                ' to Vantiq service events',
            globalType: null,
            interface: [],
            internalEventHandlers: [],
            name: name,
            partitionType: null,
            replicationFactor: 1,
            scheduledProcedures: [:]
        ]
        def eventName = name + 'Event'
        def eventType = [
            (eventName): [
                direction: (isSink ? 'INBOUND' : 'OUTBOUND'),
                eventSchema: buildResourceRef(VANTIQ_TYPES, CAMEL_MESSAGE_SCHEMA),  // FIXME: Extend if we offer
                // options
                    // here.
                isReliable: false
            ]
        ]

        def associatedRule = "/rules/${name + (isSink ? RULE_SINK_NAME_SUFFIX : RULE_SOURCE_NAME_SUFFIX)}"
        if (isSink) {
            eventType[eventName].implementingResource = associatedRule
        } else {
            service.internalEventHandlers += associatedRule
        }
        service.eventTypes = eventType

        String svcDefJson = JsonOutput.prettyPrint(JsonOutput.toJson(service))
        log.info('Creating service {}:\n{}', service.name, svcDefJson)
        Map<String, Object> retVal = [:]
        retVal.path = writeVantiqEntity(VANTIQ_SERVICES, kameletAssemblyDir, packageName,
            plainName + JSON_SUFFIX, svcDefJson, true)
        retVal.reference = buildResourceRef(VANTIQ_SERVICES, service.name as String)
        retVal.vailName = service.name
        return retVal


    }

    /**
     * Add source definition for Vantiq source for this kamelet.
     *
     * Will generate a camelConnector source defined for this kamelet using the properties and routes
     * defined.
     *
     * @param kameletAssemblyDir Path location of the base location for the project artifacts
     * @param packageName String name of the package to which this source will belong
     * @param kamName String name of the kamelet which will be used to construct the source name
     * @param routeDoc String name of the document containing the route
     * @param props Map<String, Object> The Camel properties associated with this kamelet.
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    static Map<String, Object> addSourceDefinition(Path kameletAssemblyDir, String packageName, String kamName,
                                                   String routeDoc, Map<String, Object> props) {
        log.info('Creating source for kamelet: {} in package {} using route: {}, ')
        def sourceDef = [:]
        def plainName = kamName + SOURCE_NAME_SUFFIX
        sourceDef.active = true
        sourceDef.name = packageName + PACKAGE_SEPARATOR + plainName
        sourceDef.messageType = null // FIXME: Is there a schema we know about this?  Should there be?
        sourceDef.activationConstraint = ''
        sourceDef.type = CAMEL_CONNECTOR_SOURCE_TYPE
        def camelAppConfig = [(CAMEL_RUNTIME_APPNAME): kamName,
                              (CAMEL_RUNTIME_ROUTE_DOCUMENT): routeDoc]
        def propValStubs = [:]
        props.each { aProp ->
            log.info('Creating prop value stub for {}', aProp)
            propValStubs[aProp.key] = aProp.key + PROPERTY_PLACEHOLDER_SUFFIX
        }
        log.info('propValStubs: {}', propValStubs)
        camelAppConfig.propertyValues = propValStubs
        def generalConfig = [:]
        sourceDef.config = [ (CONFIG_CAMEL_RUNTIME): camelAppConfig, (CONFIG_CAMEL_GENERAL): generalConfig]
        String srcDefJson = JsonOutput.prettyPrint(JsonOutput.toJson(sourceDef))
        log.info('Creating source {}:\n{}', sourceDef.name, srcDefJson)
        Map<String, Object> retVal = [:]
        retVal.path = writeVantiqEntity(VANTIQ_SOURCES, kameletAssemblyDir, packageName,
            plainName + JSON_SUFFIX, srcDefJson, true)
        retVal.reference = buildResourceRef(VANTIQ_SOURCES, sourceDef.name as String)
        retVal.vailName = sourceDef.name
        return retVal
    }

    /**
     * Construct basic project definition for kamelet.
     *
     * Builds project definition corresponding to the kamelet.  Kamelet defined properties become configuration
     * properties & mappings, and the components used are added as resources.
     *
     * Kamelet properties are considered 'required' unless there is a default.  Any properties that are marked with
     * format 'password' (or equivalent should such things appear) OR marked with x-descriptor of indicating that the
     * input should be hidden (currently, 'urn:alm:descriptor:com.tectonic.ui:password') are converted to be of type
     * Secret, and require the entry of a Vantiq Secret.
     *
     * @param kameletAssemblyDir Path the directory used for this kamelet-based assembly
     * @param packageName String package name we'll use for this assembly
     * @param projectName String name of the kamelet on which we're working
     * @param description String Description of the project
     * @param props Map<String, Object> Kamelet properties extracted from the definition
     * @param components List<String> List of resources references that comprise this assembly project.
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    static Map<String, Object> addProjectDefinition(Path kameletAssemblyDir, String packageName, String projectName,
                                                    String description, Map<String, Object> props,
                                                    List<String> components) {
        def project = [:]
        project.name = packageName
        project.type = 'dev'
        project.ars_relationships = []
        project.links = []
        project.tools = []
        project.views = []
        project.partitions = []
        project.isAssembly = true
        log.info('Creating visible resources')
        project.visibleResources = components.findAll {
            it.startsWith(VANTIQ_SYSTEM_PREFIX + VANTIQ_SERVICES)
        }
        log.info('finding source...')
        def sourceName = components.find {
            it.startsWith(VANTIQ_SYSTEM_PREFIX + VANTIQ_SOURCES)
        }
        log.info('Found projects source reference: {}', sourceName)
        sourceName = sourceName.substring(sourceName.lastIndexOf('/') + 1)
        log.info('Found projects source name {}', sourceName)

        project.options = [
            description: description,
            filterBitArray: 'ffffffffffffffffffffffffffffffff',
            type: 'dev',
            v: 5,
            isModeloProject: true,
        ]

        List<Map<String, Object>> resources = []
        components.each {comp ->
            resources << ([resourceReference: comp] as Map<String, Object>)
        }
        project.resources = resources

        Map<String, Map<String, Object>> cfgProps = [:]
        Map<String, Map<String, Object>> cfgMappings = [:]
        props.each { pName, o ->
            log.info("\t processing property {}: {}", pName, o)
            Map pDesc
            if (o instanceof Map) {
                pDesc = o as Map
            } else {
                throw new GradleException("prop desc not map: " + o.class.name)
            }
            log.info("\t processing property {}: {}", pName, pDesc)

            // Properties are required if there is no default...
            Map<String, Object> propDesc = [ required: pDesc.default == null ]
            // Here, we need to be wary of Groovy Truth. Check for non-null since default values of '', false, etc.
            // are "false" in Groovy Truth land.
            if (pDesc.default != null) {
                propDesc.default = pDesc.default
            }
            if (pDesc.description != null) {
                propDesc.description = pDesc.description
            } else if (pDesc.title != null) {
                propDesc.description = pDesc.title
            } else {
                propDesc.description = 'Value for ' + pDesc.pName + '.'
            }
            if (pDesc.type != null) {
                propDesc.type = StringUtils.capitalize(pDesc.type as String)
            } else {
                propDesc.type = 'String'    // FIXME -- Better Default?
            }
            if (PASSWORD_LIKE.contains(pDesc.format?.toLowerCase()) ||
                    ((String) pDesc[PROPERTY_X_DESCRIPTORS])?.contains(DISPLAY_HIDE_VALUE)) {
                // for things that are passwords or are marked to be masked in display, we will require a secret.
                propDesc.type = 'Secret'
                propDesc.description += ' (Please provide the name of the Vantiq Secret containing ' + 'this value.)'
            }
            if (pDesc.enum != null) {
                propDesc.enum = pDesc.enum
            }
            log.info('PropDesc for {} : {}', pName, propDesc)
            cfgProps[pName] = propDesc
            // FIXME -- handle secrets if appropriate
            cfgMappings[pName] = [resource: VANTIQ_SOURCES, resourceId: sourceName,
                                  property:
                                      "config.${CONFIG_CAMEL_RUNTIME}.${CAMEL_RUNTIME_PROPERTY_VALUES}.${pName}"] as
                                            Map<String, Object>
        }
        project.configurationMappings = cfgMappings
        project.configurationProperties = cfgProps
        def projectJson =  JsonOutput.prettyPrint(JsonOutput.toJson(project))
        log.info('Built project {}:\n {}', project.name, projectJson)
        Map<String, Object> retVal = [:]

        retVal.path = writeVantiqEntity(VANTIQ_PROJECTS, kameletAssemblyDir, packageName, projectName + JSON_SUFFIX,
            projectJson, false)
        retVal.reference = buildResourceRef(VANTIQ_PROJECTS, packageName, projectName)
        retVal.vailName = packageName
        return retVal
    }

    /**
     * For the routeDocumentString provided, write it out as a Vantiq document as part of the kamelets project.
     *
     * @param kameletAssemblyDir Path the directory used for this kamelet assembly
     * @param packageName String package name we'll use for this assembly
     * @param kamName String name of the kamelet on which we're working
     * @param routeDocumentString String the actual route document as extracted from the Kamelet
     * @return Map<String, Object> containing Path of document written & resourceReference to it when imported
     */
    static Map<String, Object> writeRouteDocument(Path kameletAssemblyDir,
                                                  String packageName, String kamName, String routeDocumentString) {
        String docName = kamName + YAML_ROUTE_SUFFIX
        Map<String, Object> retVal = [:]
        retVal.path = writeVantiqEntity(VANTIQ_DOCUMENTS, kameletAssemblyDir, packageName,
            docName, routeDocumentString, false)
        retVal.reference = buildResourceRef(VANTIQ_DOCUMENTS, docName)
        return retVal
    }

    /**
     * Create simple rules for routing message from source to service event or vice versa
     *
     * @param kameletAssemblyDir Path Directory into which to place results
     * @param packageName String name of package to which the rule belongs
     * @param kamName String Name of the kamelet/assembly for which the rule is being constructed
     * @param sourceName String name of the source about which the rule is being constructed
     * @param serviceName String name of th service involved
     * @param isSink Boolean is the kamelet/assembly a SINK (sends messages to the source)
     * @return Map<String, Object> containing the Path of the rule written and a resource reference to it.
     */
    static Map<String, Object> addRoutingRule(Path kameletAssemblyDir, String packageName, String kamName,
                                              String sourceName, String serviceName, Boolean isSink) {
        Map<String, Object> retVal = [:]
        String ruleName = kamName + (isSink ? RULE_SINK_NAME_SUFFIX : RULE_SOURCE_NAME_SUFFIX)\

        String ruleText
        def engine = new SimpleTemplateEngine()
        if (isSink) {
            ruleText = engine.createTemplate(SINK_RULE_TEMPLATE).make([packageName: packageName,
                                                                       ruleName: ruleName,
                                                                       serviceName: serviceName,
                                                                       sourceName: sourceName]).toString()
        } else {
            ruleText = engine.createTemplate(SOURCE_RULE_TEMPLATE).make([packageName: packageName,
                                                                         ruleName: ruleName,
                                                                         serviceName: serviceName,
                                                                         sourceName: sourceName,
                                                                         serviceEvent: 'FIXMEEventName'])
        }

        if (ruleText) {
            retVal.path = writeVantiqEntity(VANTIQ_RULES, kameletAssemblyDir, packageName,
                ruleName + VAIL_SUFFIX, ruleText, true)
            retVal.reference = buildResourceRef(VANTIQ_RULES, packageName, ruleName)
            return retVal
        } else {
            return null
        }
    }

    static Path writeVantiqEntity(String entType, Path kameletAssemblyDir, String packageName,
                                  String fileName, String content, Boolean packageIsDirectory) {

        List<String> entDirectoryList = [kameletAssemblyDir.toAbsolutePath().toString(), entType]
        if (packageIsDirectory) {
             entDirectoryList += packageName.split(Pattern.quote(PACKAGE_SEPARATOR)).toList()
        }

        // IntelliJ doesn't handle spread operator well here.
        //noinspection GroovyAssignabilityCheck
        Path entDir = Paths.get(*entDirectoryList)
        if (!Files.exists(entDir)) {
            Files.createDirectories(entDir)
        }

        if (!packageIsDirectory) {
            fileName = packageName + PACKAGE_SEPARATOR + fileName
        }
        Path entFilePath = Paths.get(entDir.toAbsolutePath().toString(), fileName)
        log.info('Creating {} definition at path: {}', entType, entFilePath.toString())
        return Files.writeString(entFilePath, content)
    }

    /**
     * Convert the route template into a reified route using the vantiq://server.config style endpoints
     *
     * @param template Map<String, Object> route template from Kamelet YAML file
     * @returns Map<String, Object> Reified route with kamelet sources & sinks replaced with Vantiq references
     */
    Map<String, Object> constructRouteText(Map<String, Object> template) {
        processStep(template)
        return template
    }

    /**
     * For a particular step, convert it & its children
     *
     * Look through the step, searching for kamelet source or sink references, replacing them with the appropriate
     * vantiq component reference.  If any step is more complex (a sub route, as it were), recurse or handle lists
     * as appropriate.
     *
     * @param step Map<String, Object> The step to process
     * @return void
     */
    void processStep(Map<String, Object> step) {
        log.debug('processStep() processing {}', step)
        for (Map.Entry<String, Object> ent: step.entrySet()) {
            String k = ent.getKey()
            Object v = ent.getValue()
            Map<String, Object> stepDef = null
            if (v instanceof Map) {
                //noinspection unchecked
                stepDef = (Map<String, Object>) v
            }
            log.debug('Found template key: {}, value: {}', k, v)
            if (k == 'from' && stepDef != null) {
                if (stepDef.containsKey('uri') && stepDef.get('uri') instanceof String &&
                    ((String) stepDef.get('uri')) == KAMELET_SOURCE) {
                    stepDef.put('uri', VANTIQ_SERVER_CONFIG_JSON)
                    log.debug('Replaced {} with{}', stepDef.get('uri'), VANTIQ_SERVER_CONFIG_JSON)
                }
                
                for (Map.Entry<String, Object> oneEntry: stepDef.entrySet()) {
                    String stepKey = oneEntry.getKey()
                    Object stepVal = oneEntry.getValue()
                
                    if (stepKey.equalsIgnoreCase('steps')
                                    || stepKey.equalsIgnoreCase('choice')
                                    || stepKey.equalsIgnoreCase('when')) {
                        if (stepVal instanceof Map) {
                            //noinspection unchecked
                            processStep((Map<String, Object>) stepVal)
                        } else if (stepVal instanceof List) {
                            //noinspection unchecked
                            processSteps((List<Map<String, Object>>) stepVal)
                        }
                    }
                }
            } else if (k == 'to' && KAMELET_SINK == v) {
                log.debug('Replacing value of key {}:{} with \'vantiq://server.config\'', k, v)
                ent.setValue('vantiq://server.config')
            } else if (k == 'to' && stepDef != null && stepDef.containsKey('uri')
                    && KAMELET_SINK == stepDef.get('uri')) {
                stepDef.put('uri', 'vantiq://server.config')
    
            }
        }
    }

    /**
     * Given a list of steps, process each one
     * @param steps List<Map<String, Object>> The steps to process
     * @return void
     */
    void processSteps(List<Map<String, Object>> steps) {
        for (Map<String, Object> step: steps) {
            processStep(step)
        }
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
            throw new GradleException('Cannot read kamelet Stream: ' + kameletToFetch)
        }
        Object raw = loader.loadFromInputStream(kameletStream)
        if (!(raw instanceof Map)) {
            throw new GradleException('Internal Error:  Expected Map from kamelStream, got ' +
                                              kameletStream.getClass().getName())
        }
        //noinspection unchecked
        Map<String, Object> parsed = (Map<String, Object>) raw
        if (parsed.size() == 0) {
            throw new GradleException('Setup Conditions: No values found in kameletStream.')
        }
        return parsed
    }

    static String buildResourceRef(String resourceType, String packageName, String resourceId) {
        if (packageName) {
            return VANTIQ_SYSTEM_PREFIX + "${resourceType}/${packageName} + ${PACKAGE_SEPARATOR} + ${resourceId}"
        } else {
            return buildResourceRef(resourceType, resourceId)
        }
    }

    static String buildResourceRef(String resourceType, String resourceId) {
        return VANTIQ_SYSTEM_PREFIX + "${resourceType}/${resourceId}"
    }

    // Basic rule to route message sent to a service to the source implementing the kamelet route
    public static final String SINK_RULE_TEMPLATE = '''
package ${packageName}
RULE ${ruleName}
WHEN EVENT OCCURS ON "/services/${packageName + '.' + serviceName}" AS svcMsg

var srcName = "${sourceName}"

// FIXME -- need tp figure out how to get message format if applicable -- may require some manual step?
PUBLISH { header: svcMsg.value.header, message: svcMsg.value.message } TO SOURCE @srcName
'''

    // Basic rule to route message sent from a source to the service representing the kamelet route
    public static final String SOURCE_RULE_TEMPLATE = '''
package ${packageName}
RULE ${ruleName}
WHEN EVENT OCCURS ON "/sources/${sourceName}" as svcMsg

PUBLISH { header: svcMsg.value.header, message: svcMsg.value.message } 
    TO SERVICE EVENT "${packageName + '.' + serviceName}/${serviceName + 'Event'}"
'''
}
