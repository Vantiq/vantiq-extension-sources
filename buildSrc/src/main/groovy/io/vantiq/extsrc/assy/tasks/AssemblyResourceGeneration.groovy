/*
 * Copyright (c) 2024 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.assy.tasks

import static org.snakeyaml.engine.v2.common.FlowStyle.BLOCK

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.Dump

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import java.util.regex.Pattern

/**
 * Gradle task to build assembly specifications from Apache Camel Kamelet definitions.
 */

@Slf4j
class AssemblyResourceGeneration extends DefaultTask {
    public static final String VANTIQ_SERVER_CONFIG = 'vantiq://server.config'
    public static final String VANTIQ_SERVER_CONFIG_JSON = VANTIQ_SERVER_CONFIG + '?consumerOutputJsonStream=true'
    public static final String VANTIQ_SERVER_CONFIG_STRUCTURED = VANTIQ_SERVER_CONFIG + '?structuredMessageHeader=true'
    public static final String VANTIQ_SERVER_CONFIG_JSON_STRUCTURED = VANTIQ_SERVER_CONFIG_JSON +
        '&structuredMessageHeader=true'

    public static final String KAMELETS_RESOURCE_FOLDER = 'kamelets'
    public static final String KAMELETS_RESOURCE_PATH = KAMELETS_RESOURCE_FOLDER + '/'
    public static final String YAML_FILE_TYPE = '.yaml'
    public static final String YML_FILE_TYPE = '.yml'
    public static final String JAVASCRIPT_FILE_TYPE = '.js'
    public static final String SOURCE_KAMELET_PREFIX = '-source.kamelet'
    public static final String SINK_KAMELET_PREFIX = '-sink.kamelet'
    public static final String SOURCE_KAMELET_YAML_SUFFIX = SOURCE_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SOURCE_KAMELET_YML_SUFFIX = SOURCE_KAMELET_PREFIX + YML_FILE_TYPE
    public static final String SINK_KAMELET_YAML_SUFFIX = SINK_KAMELET_PREFIX + YAML_FILE_TYPE
    public static final String SINK_KAMELET_YML_SUFFIX = SINK_KAMELET_PREFIX + YML_FILE_TYPE
    public static final String PROPERTY_X_DESCRIPTORS = 'x-descriptors'
    public static final String DISPLAY_HIDE_VALUES = [
        'urn:alm:descriptor:com.tectonic.ui:password',
        'urn:camel:group:credentials'
        ]
    // The following is used in an all-lower-case comparison, so it should be entirely lower case words
    public static final List<String> PASSWORD_LIKE = ['password']

    public static final String KAMELET_SOURCE = 'kamelet:source'
    public static final String KAMELET_SINK = 'kamelet:sink'
    
    public static final String ASSEMBLY_PACKAGE_BASE = 'com.vantiq.extsrc.camel.kamelets'
    public static final String PACKAGE_SEPARATOR = '.'

    public static final String FROM_ROUTE_TAG = 'from'
    public static final String ID_ROUTE_TAG = 'id'
    public static final String ROUTE_TEMPLATE_TAG = 'route-template'
    public static final String TO_ROUTE_TAG = 'to'
    public static final String URI_ROUTE_TAG = 'uri'

    // Vantiq specific names for various things
    public static final String PROPERTY_BAG_PROPERTY = 'ars_properties'
    public static final String ADDITIONAL_CONFIG_PROPERTIES = 'additionalConfigProps'
    public final static String VANTIQ_SYSTEM_PREFIX = 'system.'
    public static final String VANTIQ_DOCUMENTS = 'documents'
    public static final String VANTIQ_PROCEDURES = 'procedures'
    public static final String VANTIQ_PROJECTS = 'projects'
    public static final String VANTIQ_RULES = 'rules'
    public static final String VANTIQ_SERVICES = 'services'
    public static final String VANTIQ_SOURCES = 'sources'
    public static final String VANTIQ_SOURCE_IMPLEMENTATION = "sourceimpls"
    public static final String VANTIQ_TYPES = 'types'
    // This is the list of types that do not need the VANTIQ_SYSTEM_PREFIX on their types by convention in the UI.
    // In the server, either form will work, but things can be a bit pickier at the interface level.
    // Note that, while true, adding VANTIQ_SERVICES to this list ends up with the system not understanding that
    // these services should be part of the interface. OTOH, these need to be here to act correctly.
    public static final String GRANDFATHERED_TYPES = [VANTIQ_DOCUMENTS, VANTIQ_PROCEDURES, VANTIQ_PROJECTS,
                                                      VANTIQ_RULES, VANTIQ_SOURCE_IMPLEMENTATION, VANTIQ_TYPES]

    public enum ResourceType {
        TYPE(1),
        RULE(2),
        PROCEDURE(3),
        SOURCE(4),
        OBSOLETE3(5),
        XANALYTICSMODEL(6),
        CONFIGURATION(7),
        SITUATION(8),
        SUBSCRIPTION(9),

        TOPIC(10),
        COLLABORATIONTYPE(11),
        APP(12),
        ANALYTICSSOURCE(13),
        EVENTSTREAM(14),
        CLIENT(15),
        COLLABORATION(16),
        SCHEDULEDEVENT(17),
        ACTIVITYPATTERN(18),
        DOCUMENT(19),

        RCSPAGE(20),
        ERRORQUERYRESULTS(21),
        ERROR(22),
        GRAPH(23),
        NODE(24),
        LOGLIST(25),
        ACCESSTOKEN(26),
        AUTOPSYLIST(27),
        AUTOPSY(28),
        FINDRECORDRESULTS(29),

        DEPLOYMENTRESULTS(30),
        DEBUGCONFIG(31),
        UPDATERECORD(32),
        OBSOLETE1(33),
        GROUPS(34),
        SERVICES(35),
        USER(36),
        PROFILES(37),
        SITUATIONLIST(38),
        COLLABORATIONLIST(39),

        AUDITS(40),
        AUDITLIST(41),
        NAMESPACE(42),
        ORG(43),
        NODECONFIG(44),
        SYSTEMMODEL(45),
        EVENTTYPE(46),
        EVENTLIST(47),
        SECRETS(48),
        DEPLOYCONFIG(49),

        DEPLOYENVIRONMENT(50),
        GROUP(51),
        DELEGATEDREQUEST(52),
        PROFILE(53),
        TILEDOCK(54),
        TESTLIST(55),
        TEST(56),
        TESTREPORTLIST(57),
        TESTREPORT(58),
        TESTSUITELIST(59),

        TESTSUITE(60),
        TESTSUITEREPORT(61),
        UNITTESTREPORT(62),
        SERVICE(63),
        CATALOGSERVICE(64),
        EVENTGENERATOR(65),
        CAPTURE(66),
        CATALOG(67),
        CATALOGMEMBER(68),
        SOURCEIMPL(69),

        APPCOMPONENT(70),
        K8SCLUSTERS(71),
        CLIENTCOMPONENT(72),
        ASSEMBLY(73),
        ASSEMBLYCONFIG(74),
        DESIGNMODEL(75),
        STORAGEMANAGER(76),
        SERVICECONNECTORS(77),
        SERVICECONNECTOR(78),
        SEMANTICINDEX(79),

        LLM(80),
        AICOMPONENT(81)

        private final Integer value

        ResourceType(Integer val) {
            this.value = val
        }

        static byName(String name) {
            ResourceType retVal = null
            String target = name.toUpperCase()
            if (target == 'SYSTEM.SERVICES') {
                // Handle lookup for new style services here
                target = 'SERVICE'
            } else if (target.startsWith('SYSTEM.')) {
                target = target.substring('SYSTEM.'.length())
            }
            try {
                retVal = target as ResourceType
                log.debug('Value of {}: {}', target, retVal)
            } catch (IllegalArgumentException iae) {
                log.debug("No ResourceType value for {}", target)
                target = target.substring(0, target.length() - 1) // Remove trailing s
                log.debug(' ... looking for {}', target)
                retVal = target as ResourceType
                log.debug('Value of {} (singular): {}', target, retVal)
            }
            log.info('Resource type for {}: {} ({})', name, retVal, retVal.value)
            retVal
        }
    }

    public static final String RULE_SINK_NAME_SUFFIX = '_svcToSrc'
    public static final String RULE_SOURCE_NAME_SUFFIX = '_srcToSvc'
    public static final String SERVICE_NAME_SUFFIX = '_service'
    public static final String SOURCE_NAME_SUFFIX = '_source'

    public static final String DEPLOYMENT_SERVICE_NAME_BASE = 'Deployment'
    public static final String DEPLOYMENT_PROCEDURE_NAME = 'deployToK8s'

    // Definitions from CamelConnector that we may need
    public static final String CAMEL_CONNECTOR_DEPLOYMENT_SERVICE = 'ConnectorDeployment'
    public static final String CAMEL_CONNECTOR_DEPLOYMENT_PACKAGE = 'com.vantiq.extsrc.camelconn'

    public static final String CAMEL_CONNECTOR_DEPLOYMENT_PROCEDURE = DEPLOYMENT_PROCEDURE_NAME

    public static final String CAMEL_SOURCE_TYPE = 'CAMEL'
    public static final String CONFIG_CAMEL_RUNTIME = 'camelRuntime'
    public static final String CONFIG_CAMEL_GENERAL = 'general'
    public static final String CAMEL_RUNTIME_APPNAME = 'appName'
    public static final String CAMEL_RUNTIME_PROPERTY_VALUES = 'propertyValues'
    public static final String GENERAL_ADDITIONAL_LIBRARIES = 'additionalLibraries'
    public static final String CAMEL_RAW_HANDLING = 'rawValuesRequired'
    // TODO: Add ability to add some overrides here where we want Camel RAW handling to be performed
    public static final String CAMEL_DISCOVERED_RAW = 'discovered'
    public static final String CAMEL_RUNTIME_ROUTE_DOCUMENT = 'routesDocument'
    public static final String YAML_ROUTE_SUFFIX = '.routes.yaml'
    public static final String JSON_SUFFIX = '.json'
    public static final String VAIL_SUFFIX = '.vail'
    public static final String CAMEL_MESSAGE_SCHEMA = 'com.vantiq.extsrc.camelcomp.message'

    public static final String OVERVIEW_SUFFIX = '.overview.md'
    public static final String OVERVIEW_SOURCE_DISCLAIMER =
        '\n\n> _This description has been provided by the Kamelet from which this assembly was generated. ' +
        'Consider that context when using the information contained herein._\n\n'
    public static final String VANTIQ_NOTES_SUFFIX = '.vantiqNotes.md'

    public final Project project
    public static String packageSafeCamelVersion
    public static String rawCamelVersion
    public static final String RESOURCE_BASE_DIRECTORY = 'src/main/resources'
    public static final String VANTIQ_NOTES_DIRECTORY = 'vantiqNotes'
    public static final String ROUTES_OVERRIDE_DIRECTORY = 'routeOverrides'
    public static final String SOURCE_CONFIG_OVERRIDE_DIRECTORY = 'sourceConfigOverrides'

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
            throw new GradleException('Internal Error: buildDir is not present or is not a directory: ' + buildDir)
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

        def kjVersion = kameletJarFilename.substring(kameletJarFilename.lastIndexOf('/') + 1) - '.jar'
        rawCamelVersion = kjVersion - 'camel-kamelets-'
        packageSafeCamelVersion = 'v' + rawCamelVersion.replaceAll('\\.', '_')
        log.lifecycle('Using Kamelets from Camel version {}.', rawCamelVersion)

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
                project.getLogger().lifecycle('    {} kamelets ignored -- (neither source nor sink as ' +
                    'per naming conventions).', discards.length)
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
        String typeLabel = (String) ((Map<String, String>) ((Map<String, Object>) yamlMap
            .get('metadata')).get('labels')) .get('camel.apache.org/kamelet.type')
        if (typeLabel != 'sink' && typeLabel != 'source')  {
            throw new GradleException('Unexpected camel.apache.org/kamelet.type label: ' + typeLabel)
        } else if (isSink != (typeLabel == 'sink')) {
            throw new GradleException('Name vs. camel.apache.org/kamelet.type label mismatch: ' + typeLabel)
        }

        //noinspection unchecked
        String title =
            (String) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
                .get('definition')).get('title')
        log.info('Found spec for {}', title)
        String overview = (String) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
            .get('definition')).get('description')
        List<String> requiredProps = (List<String>) ((Map<String, Object>)((Map<String, Object>) yamlMap.get('spec'))
            .get('definition')).get('required')
        log.debug('Required Props from kamelet definition: {}', requiredProps ?: '<<empty>>')
        List<String> dependencies = (List<String>) ((Map<String, Object>) yamlMap.get('spec')).get('dependencies')
        // Dependencies can also be declared at the type level, so grab those as well.
        Map<String, Object> types = (Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
            .get('types')
        if (types != null) {
            types.forEach (( k, v ) -> {
                if (v.containsKey('dependencies')) {
                    List<String> tDeps = (List<String>) v.get('dependencies')
                    dependencies.putAll(tDeps)
                    log.debug('For type {}, found dependencies: {}', k, tDeps)
                }
            })
        }
        log.debug('Kamelet-defined dependencies: {}', dependencies)

        // Read any defined properties.  These will become properties for our assembly.
        //noinspection unchecked
        Map<String, Object> props =
            (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) yamlMap.get('spec'))
                .get('definition')).get('properties')
        if (log.isDebugEnabled()) {
            log.debug('    containing {} properties:', props.size())
            props.forEach((k, v) -> {
                //noinspection unchecked
                log.debug('        name: {}, [type: {}, desc: {}]', k,
                    ((Map<String, String>) v).get('type'),
                    ((Map<String, String>) v).get('description'))
            })
        }
        String kamName = kameletDefinition.substring(0, kameletDefinition.indexOf('.kamelet.yaml'))
        kamName = kamName.replace('-', '_')
        String packageName = String.join(PACKAGE_SEPARATOR, ASSEMBLY_PACKAGE_BASE, packageSafeCamelVersion, kamName)

        DumpSettings yamlDumpSettings = DumpSettings.builder()
            .setIndent(4)
                // FLOW (default)serializes any embedded maps.
                // This will recurse.
            .setDefaultFlowStyle(BLOCK)
            .build()
        Dump yamlDumper = new Dump(yamlDumpSettings)

        // Now, extract the route template.  This will become our route document after we change
        // kamelet:source or kamelet:sink to vantiq server URLs

        //noinspection unchecked
        Map<String, Object> template = (Map<String, Object>) ((Map<String, Object>) yamlMap
            .get('spec')).get('template')
        String originalTemplateString = yamlDumper.dumpToString(template)
        log.debug('\n>>>   Original Template as Yaml:\n{}', originalTemplateString)

        List<Map<String, Object>> routeSpec = constructRouteText(kamName, template)
        log.info('YAML document Map: {}', routeSpec)

        // Now, generate & populate results
        Path assemblyRoot = Paths.get(outputRoot.toAbsolutePath().toString(), kamName + "_${packageSafeCamelVersion}")
        if (!Files.exists(assemblyRoot)) {
            log.info('Creating kamelet-specific project directory: {}', assemblyRoot)
            assemblyRoot = Files.createDirectories(assemblyRoot)
        }
        String routesDocumentString = yamlDumper.dumpToString(routeSpec)

        // If we have an override for the routes document, pick that up & use it instead.  This is sometimes required
        // when there are flaws or enhancements to the route provided in the Kamelet.

        File routesFile = project.file(RESOURCE_BASE_DIRECTORY + File.separator + ROUTES_OVERRIDE_DIRECTORY +
            File.separator + kamName + File.separator + packageSafeCamelVersion + File.separator +  kamName +
            YAML_ROUTE_SUFFIX)
        log.debug('For {}, checking for replacement routes document found at {}', kamName, routesFile.absolutePath)
        if (routesFile?.exists()) {
            log.debug('For {}, using replacement routes document found at {}', kamName, routesFile.absolutePath)
            String newRoutesDocString = ''

            routesFile.readLines().each { aLine ->
                newRoutesDocString += aLine + '\n'
            }
            if (!newRoutesDocString) {
                log.error("Ignoring prebuilt routes document {} as it contains no content.", routesFile.absolutePath)
            } else {
                routesDocumentString = newRoutesDocString
            }
        } else {
            log.debug('No replacement routes document found for {}', kamName)
        }
        if (log.isDebugEnabled()) {
            log.debug('\nResults for Kamelet: {}', kamName)
            log.debug('\n>>>   Properties:')
            props.forEach((name, val) -> {
                //noinspection unchecked
                log.debug('    Name: {} :: [title: {}, type: {}, desc: {}]', name,
                    ((Map<String, String>) val).get('title'),
                    ((Map<String, String>) val).get('type'),
                    ((Map<String, String>) val).get('description'))
            })
            log.debug('\n>>>   Converted Template as Yaml:\n{}', routesDocumentString)
        }
        log.info('\nWriting project to: {}', assemblyRoot)

        Map routeDoc = writeRoutesDocument(assemblyRoot, packageName, kamName, routesDocumentString)
        Map sourceDef = addSourceDefinition(assemblyRoot, packageName, kamName,
            routeDoc.path.fileName.toString(), props, dependencies)
        log.debug('Created sourceDef: {}', sourceDef)
        Map overviewDoc = writeOverviewDocument(assemblyRoot, packageName, kamName,
            overview, title, sourceDef.vailName as String)
        String projDesc = constructOverviewText(true, title, overview,
            sourceDef.vailName as String)
        Map notesDoc = writeVantiqNotesDocument(assemblyRoot, packageName, kamName)
        Map serviceDef = addServiceDefinition(assemblyRoot, packageName, kamName, isSink)
        log.debug('Created serviceDef: {}', serviceDef)

        Map ruleDef = addRoutingRule(assemblyRoot, packageName, kamName,
            sourceDef.vailName as String, (serviceDef.vailName as String) - (packageName + PACKAGE_SEPARATOR), isSink)
        log.debug('Created rule def: {}', ruleDef)

        // To ease use, we'll add a service & procedure to deploy this connector to Kubernetes.  This deployment
        // makes use of the base function supplied as part of the CamelConnector assembly which contains the
        // procedure to perform the actual work.  The service procedure provided here collects the parameters and
        // adds the source name (since we're generating that and, consequently, know it).

        Map deploymentProc = addDeploymentProcedure(assemblyRoot, packageName, kamName, sourceDef.vailName as String,
            DEPLOYMENT_SERVICE_NAME_BASE)
        Map deploymentService = addDeploymentServiceDefinition(assemblyRoot, packageName, kamName,
            generateDeploymentServiceName(kamName, DEPLOYMENT_SERVICE_NAME_BASE), K8S_DEPLOYMENT_SERVICE_INTERFACE)
        List<String> componentList = [
            routeDoc.reference as String,
            sourceDef.reference as String,
            serviceDef.reference as String,
            deploymentProc.reference as String,
            deploymentService.reference as String
        ]
        if (overview != null) {
            componentList << (overviewDoc.reference as String)
        }
        if (notesDoc != null) {
            componentList << (notesDoc.reference as String)
        }
        if (ruleDef) {
            componentList << (ruleDef.reference as String)
        }
        log.debug("Adding project def: addProjDef({}, {}, {}, {}, {}, {}",
            assemblyRoot, packageName, kamName, title, props, componentList)
        //noinspection GroovyUnusedAssignment       // Useful for debugging...
        Map projectDef = addProjectDefinition(assemblyRoot, packageName, kamName,
            projDesc, props, requiredProps, componentList, serviceDef.service as Map)
        log.debug('Created projectDef: {}', projectDef)
    }

    /**
     * Add service definition for event handler
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
        def eventName = plainName + 'Event'
        def eventType = [
            (eventName): [
                direction: (isSink ? 'INBOUND' : 'OUTBOUND'),
                eventSchema: CAMEL_MESSAGE_SCHEMA,
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
        retVal.service = service
        return retVal
    }

    /**
     * Create service procedure for deploying this connector to Kubernetes (via Vantiq)
     *
     * This procedure, generated from the K8S_DEPLOYMENT_PROCEDURE_TEMPLATE, gathers parameters and calls the
     * CamelConnector.deployToK8s() procedure (from the CamelConnector assembly) to do the work.  This procedure
     * extends that by providing the source name for the source generated by the assembly we're creating.
     *
     * @param kameletAssemblyDir Path Directory into which to place results
     * @param packageName String name of package to which the rule belongs
     * @param kamName String Name of the kamelet/assembly for which the rule is being constructed
     * @param sourceName String name of the source about which the rule is being constructed
     * @param serviceBase String base name of the service involved
     * @return Map<String, Object> containing the Path of the procedure written and a resource reference to it.
     */
    static Map<String, Object> addDeploymentProcedure(Path kameletAssemblyDir, String packageName, String kamName,
                                                      String sourceName, String serviceBase) {
        Map<String, Object> retVal = [:]
        String serviceName = generateDeploymentServiceName(kamName, serviceBase)
        String procName = serviceName + PACKAGE_SEPARATOR + DEPLOYMENT_PROCEDURE_NAME
        String procFileName = serviceName + '_' + DEPLOYMENT_PROCEDURE_NAME + VAIL_SUFFIX
        def engine = new SimpleTemplateEngine()
        String procText = engine.createTemplate(K8S_DEPLOYMENT_PROCEDURE_TEMPLATE).make(
            [packageName: packageName,
             serviceName: serviceName,
             sourceName: sourceName,
             procedureName: procName,
             camConnectorPackage: CAMEL_CONNECTOR_DEPLOYMENT_PACKAGE,
             camConnectorDepService: CAMEL_CONNECTOR_DEPLOYMENT_SERVICE,
             camConnectorDepProcedure: CAMEL_CONNECTOR_DEPLOYMENT_PROCEDURE
            ]).toString()

        if (procText) {
            retVal.path = writeVantiqEntity(VANTIQ_PROCEDURES, kameletAssemblyDir, packageName,
                procFileName, procText, true)
            retVal.reference = buildResourceRef(VANTIQ_PROCEDURES, packageName, procName)
            return retVal
        } else {
            return null
        }
    }

    static String generateDeploymentServiceName(String kamName, String serviceBase) {
        return kamName.capitalize() + '_' + serviceBase
    }

    /**
     * Add service definition for deployment service.
     *
     * This incorporates the deployment procedure created by addDeploymentProcedure().
     *
     * @param kameletAssemblyDir Path location of the base location for the project artifacts
     * @param packageName String name of the package to which this service will belong
     * @param kamName String name of the kamelet which will be used to construct the service name
     * @param serviceName String name of the service we're creating
     * @param interfaceSpec List<Map> service's interface definition
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    static Map<String, Object> addDeploymentServiceDefinition(Path kameletAssemblyDir, String packageName,
                                                              String kamName, String serviceName,
                                                              List<Map> interfaceSpec) {
        def name = packageName + PACKAGE_SEPARATOR + serviceName
        def service = [
            active: true,
            ars_relationships: [],
            description: 'Service for deploying connector to a Kubernetes cluster defined in Vantiq',
            globalType: null,
            interface: [],
            internalEventHandlers: [],
            name: name,
            partitionType: null,
            replicationFactor: 1,
            scheduledProcedures: [:]
        ]
        if (interfaceSpec) {
            service.interface = interfaceSpec
        }

        String svcDefJson = JsonOutput.prettyPrint(JsonOutput.toJson(service))
        log.info('Creating service {}:\n{}', service.name, svcDefJson)
        Map<String, Object> retVal = [:]
        retVal.path = writeVantiqEntity(VANTIQ_SERVICES, kameletAssemblyDir, packageName,
            serviceName + JSON_SUFFIX, svcDefJson, true)
        retVal.reference = buildResourceRef(VANTIQ_SERVICES, service.name as String)
        retVal.vailName = service.name
        retVal.service = service
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
     * @param declaredDependencies List<String> dependencies found in the kamelet file
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    Map<String, Object> addSourceDefinition(Path kameletAssemblyDir, String packageName, String kamName,
                                                   String routeDoc, Map<String, Object> props,
                                                   List<String> declaredDependencies) {
        log.info('Creating source for kamelet: {} in package {} using route: {}, ')
        def sourceDef = [:]
        def plainName = kamName + SOURCE_NAME_SUFFIX
        sourceDef.active = true
        sourceDef.name = packageName + PACKAGE_SEPARATOR + plainName
        sourceDef.messageType = CAMEL_MESSAGE_SCHEMA
        sourceDef.activationConstraint = ''
        sourceDef.type = CAMEL_SOURCE_TYPE
        Map<String, Object> camelAppConfig = [(CAMEL_RUNTIME_APPNAME): kamName,
                              (CAMEL_RUNTIME_ROUTE_DOCUMENT): routeDoc]
        List<String> propNameList = []
        List<String> rawHandlingRequired = []

        // Note: Here, we could have the CAMEL source type define camelRuntime.propertyValues as configurable.
        // However, if we did that, on installation, the user would have to provide an object with the various lower
        // level items & their values.  And any descriptive information provided by the kamelet definition would be
        // lost.  Consequently, it seems preferable for each source to define the set it needs and let the assembly
        // installation and configuration mechanism take its course.
        props.each { aProp ->
            // Placeholder values for each property here are not required. But we need to generate the list of
            // configurable properties to keep the assembly substitution mechanism happy.
            //
            // Note that adding all the placeholders was problematic when not all the properties are provided.  When
            // an assembly installer configured their assembly & did not provide a value for some (optional)
            // property, the placeholder value is replaced, but replaced with null.  So, we got properties sent down
            // with the value null.  We work around that (indeed, the CamelConnector config handler just ignores
            // these as good practice), but avoidance at the higher level seems preferable.
            propNameList << new String().join('.', ['config',
                                                    CONFIG_CAMEL_RUNTIME,
                                                    CAMEL_RUNTIME_PROPERTY_VALUES,
                                                    aProp.key])
            if (!(aProp.value instanceof Map)) {
                throw new GradleException('Unexpected type for property description: ' + aProp.value?.class?.name)
            }
        }
        log.debug('Configurable properties: {}', propNameList)
        log.debug('Configuration properties that required raw handling: {}', rawHandlingRequired)
        if (propNameList) {
            sourceDef[PROPERTY_BAG_PROPERTY] = [:]
            sourceDef[PROPERTY_BAG_PROPERTY][ADDITIONAL_CONFIG_PROPERTIES] = propNameList
            // As noted above, we don't need all the placeholders inserted. But we do need their container...
            camelAppConfig[CAMEL_RUNTIME_PROPERTY_VALUES] = [:]
            if (rawHandlingRequired) {
                camelAppConfig[CAMEL_RAW_HANDLING] = [:]
                camelAppConfig[CAMEL_RAW_HANDLING][CAMEL_DISCOVERED_RAW] = rawHandlingRequired
            }
        }
        def generalConfig = [:]
        if (declaredDependencies && declaredDependencies.size() > 0) {
            List<String> additionalLibraries = new ArrayList<>(declaredDependencies.size())
            declaredDependencies.forEach( ( dep ) -> {
                if (dep.startsWith('camel:')) {
                    additionalLibraries.add('org.apache.camel:camel-' + dep.substring('camel:'.length()))
                } else if (dep.startsWith('mvn:')) {
                    additionalLibraries.add(dep.substring('mvn:'.length()))
                } // Ignore github things...
            })
            generalConfig[GENERAL_ADDITIONAL_LIBRARIES] = additionalLibraries
        }

        // In some cases, we may have to override the source configuration in some cases.  For this, we'll check for
        // an override document.  If present, we'll take the override values present and override what's been
        // generated here. At some point, we may need to look at a deeper merge. But, for now, this is sufficient.

        File configOverrideFile = project.file(RESOURCE_BASE_DIRECTORY + File.separator +
            SOURCE_CONFIG_OVERRIDE_DIRECTORY +
            File.separator + kamName + File.separator + packageSafeCamelVersion + File.separator +  kamName +
            JSON_SUFFIX)

        if (configOverrideFile.exists()) {
            log.debug('Taking config defaults from {}', configOverrideFile.absolutePath)
            Map<String, Object> configDefault = new JsonSlurper().parse(configOverrideFile) as Map<String, Object>
            log.debug('Source config defaults for {}: {}', kamName, configDefault)
            camelAppConfig << configDefault.get(CONFIG_CAMEL_RUNTIME)
            generalConfig << configDefault.get(CONFIG_CAMEL_GENERAL)
        } else {
            log.debug('No source config found for {}', configOverrideFile.absolutePath)
        }
        sourceDef.config = [ (CONFIG_CAMEL_RUNTIME): camelAppConfig, (CONFIG_CAMEL_GENERAL): generalConfig]
        String srcDefJson = JsonOutput.prettyPrint(JsonOutput.toJson(sourceDef))
        log.debug('Creating source {}:\n{}', sourceDef.name, srcDefJson)
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
     * @param kameletAssemblyDir Path the directory used for this kamelet-based assembly
     * @param packageName String package name we'll use for this assembly
     * @param projectName String name of the kamelet on which we're working
     * @param description String Description of the project
     * @param props Map<String, Object> Kamelet properties extracted from the definition
     * @param reqProps List<String> Kamelet properties listed as required
     * @param components List<String> List of resources references that comprise this assembly project.
     * @param serviceDef Map<String, Object> Definition of the service used here.
     * @returns Map<String, Object> containing Path written & resourceReference
     */
    static Map<String, Object> addProjectDefinition(Path kameletAssemblyDir, String packageName, String projectName,
                                                    String description, Map<String, Object> props,
                                                    List<String> reqProps, List<String> components,
                                                    Map<String, Object> serviceDef) {
        def project = [:]
        project.name = packageName
        project.type = 'dev'
        project.ars_relationships = []
        project.links = []
        project.tools = []
        project.views = []
        project.partitions = []
        project.isAssembly = true

        project.options = [
            description: description,
            filterBitArray: 'ffffffffffffffffffffffffffffffff',
            type: 'dev',
            v: 5,
            isModeloProject: true,

            // Add exclusions for thing we bring in from other assemblies.  We don't want to include these files in our
            // definition -- there should be only one copy/instance, not one per assembly.
            exclusionList: [buildExcListReference('type', CAMEL_MESSAGE_SCHEMA),
                            buildExcListReference('sourceimpl', CAMEL_SOURCE_TYPE),
                            buildExcListReference('system.services', CAMEL_CONNECTOR_DEPLOYMENT_PACKAGE +
                                 PACKAGE_SEPARATOR +
                                 CAMEL_CONNECTOR_DEPLOYMENT_SERVICE),
                            buildExcListReference('procedure', CAMEL_CONNECTOR_DEPLOYMENT_PACKAGE +
                                 PACKAGE_SEPARATOR +
                                 CAMEL_CONNECTOR_DEPLOYMENT_SERVICE + PACKAGE_SEPARATOR +
                                 CAMEL_CONNECTOR_DEPLOYMENT_PROCEDURE),
                            // Need to include the generated javascript that's included for referenced or defined
                            // services
                            buildExcListReference('document', CAMEL_CONNECTOR_DEPLOYMENT_PACKAGE +
                                 PACKAGE_SEPARATOR +
                                 CAMEL_CONNECTOR_DEPLOYMENT_SERVICE + JAVASCRIPT_FILE_TYPE)
            ],
        ]

        // Insert the config properties & mappings first so they'll be known if/when encountered
        Map<String, Map<String, Object>> cfgProps = [:]
        Map<String, List<Map<String, Object>>> cfgMappings = [:]
        def sourceName
        props.each { pName, o ->
            log.debug("\t processing property {}: {}", pName, o)
            Map pDesc
            if (o instanceof Map) {
                pDesc = o as Map
            } else {
                throw new GradleException("prop desc not map: " + o.class.name)
            }
            log.debug("\t processing property {}: {}", pName, pDesc)

            // Properties are required if there is no default...
            Map<String, Object> propDesc = [:]
            if (pDesc.required == null && reqProps && reqProps.contains(pName)) {
                // If property itself has no "required" entry but the outer level does, set it on the property for
                // our processing.  This allows us to streamline this handling.
                pDesc.required = true
            }
            log.trace('Pdesc.required for {}: {}', pName, pDesc.required)
            if (pDesc.required instanceof Boolean) {
                propDesc.required = pDesc.required
            } else if (pDesc.required instanceof String) {
                propDesc.required = ((String) pDesc.required).toBoolean()
            } else if (pDesc.required != null) {
                throw new GradleException('Unexpected value/type for property ' + propDesc.pName + ': type: ' +
                        propDesc.required?.class?.name + ', value: ' + propDesc.required)
            } // Else, it's null so ignore it.

            // Here, we need to be wary of Groovy Truth. Check for non-null since default values of '', false, etc.
            // are "false" in Groovy Truth land.
            if (pDesc.default != null) {
                propDesc.default = pDesc.default
                // Kamelets allow a required parameter to have a default value, Vantiq assemblies do not.
                // If we have a default value, don't set the assembly config prop "required" flag.
                if (propDesc.required != null && (pDesc.required as Boolean == true)) {
                    propDesc.remove('required')
                    log.debug('Project {} :: propDesc.required for {}: removed due to default value of "{}"',
                        projectName, pName, propDesc.default)
                }
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
                propDesc.type = 'String'
            }
            // TODO: Secret type parameters re delivered to the source as resource refs to the secret.  The secret
            //  expansion doesn't handle this.  Make server-side changes to improve this situation, then re-enable
            //  this enforcement of secret values.
//            if (PASSWORD_LIKE.contains(pDesc.format?.toLowerCase()) ||
//                (pDesc[PROPERTY_X_DESCRIPTORS] != null &&
//                    DISPLAY_HIDE_VALUES.contains(((String) pDesc[PROPERTY_X_DESCRIPTORS])))) {
//                // for things that are passwords or are marked to be masked in display, we will require a secret.
//                propDesc.type = 'Secret'
//                propDesc.description += ' (Please provide the name of the Vantiq Secret containing this value.)'
//            }
            if (PASSWORD_LIKE.contains(pDesc.format?.toLowerCase()) ||
                (pDesc[PROPERTY_X_DESCRIPTORS] != null &&
                    DISPLAY_HIDE_VALUES.contains(((String) pDesc[PROPERTY_X_DESCRIPTORS])))) {
                // for things that are passwords or are marked to be masked in display, tell camel to use RAW handling
                propDesc.description += ' (We suggest that you store this value in a Vantiq Secret and use ' +
                    '"@secrets(<<secret name>>)" for the value of this property.)'
            }
            if (pDesc.enum != null) {
                // Present the enumeration as a drop down list. Adapt structure to that used by the assembly mechanism.
                propDesc.enumValues = pDesc.enum
                propDesc.type = 'Enum'  // UI seems to treat this as an alias for String, which is fine for us.
            }
            log.debug('PropDesc for {} : {}', pName, propDesc)
            cfgProps[pName] = propDesc
            log.debug('finding source...')
            sourceName = components.find {
                it.startsWith('/' + VANTIQ_SYSTEM_PREFIX + VANTIQ_SOURCES)
            }
            log.debug('Found projects source reference: {}', sourceName)
            sourceName = sourceName.substring(sourceName.lastIndexOf('/') + 1)
            log.debug('Found projects source name {}', sourceName)

            Map<String, Object> newMapping = [resource: VANTIQ_SOURCES, resourceId: sourceName,
                              property:
                                  "config.${CONFIG_CAMEL_RUNTIME}." +
                                      "${CAMEL_RUNTIME_PROPERTY_VALUES}.${pName}"]
            if (cfgMappings[pName]) {
                cfgMappings[pName] << newMapping
            } else {
                cfgMappings[pName] = [newMapping]
            }
        }
        project.configurationProperties = cfgProps
        project.configurationMappings = cfgMappings

        List<Map<String, Object>> resources = []
        String procCompPrefix = '/' + VANTIQ_PROCEDURES + '/'
        components.each {comp ->
            String[] resRefParts = comp.substring(1).split('/')
            log.info('Looking for type for {} from {}, split as {}', resRefParts[1], comp,
                resRefParts)
            ResourceType rType = ResourceType.byName(resRefParts[0] as String)
            log.info('Type value for {}; {} ({})', resRefParts[1], rType, rType.value)
            Map<String, Object> thisResource
            if (comp.startsWith(procCompPrefix)) {
                def procParts = (comp - procCompPrefix).split('\\.')
                String svcName = procParts[-2]
                thisResource = [resourceReference: comp,
                                name: resRefParts[1],
                                label: resRefParts[1],
                                serviceName: svcName,
                                type: rType.value,
                ] as Map<String, Object>
            } else {
                thisResource = [resourceReference: comp,
                                name: resRefParts[1],
                                label: resRefParts[1],
                                type: rType.value,
                ] as Map<String, Object>
            }
            resources << thisResource
            log.debug('Resource: {}', thisResource)
        }
        project.resources = resources

        // Things seem to work more consistently when visibleResources are after resources.
        log.info('Creating visible resources')
        log.debug('ServiceDef for {}: {}', project.name, serviceDef)
        project.visibleResources = components.findAll {
            it.startsWith('/' + VANTIQ_SYSTEM_PREFIX + VANTIQ_SERVICES) ||
                it.startsWith('/' + VANTIQ_SERVICES) ||
                it.startsWith('/' + VANTIQ_DOCUMENTS) ||
                it.startsWith('/'  + VANTIQ_SYSTEM_PREFIX + VANTIQ_DOCUMENTS)
        }.collect { comp ->
            if (comp.startsWith('/' + VANTIQ_SYSTEM_PREFIX + VANTIQ_SERVICES)) {
                String desc
                if(comp.endsWith(DEPLOYMENT_SERVICE_NAME_BASE)) {
                    desc = "Assembly ${project.name}'s deployment service."
                } else {
                    desc = "Assembly ${project.name}'s service bridging source ${sourceName} " +
                        "and event ${-> serviceDef.eventTypes ? ((Map) serviceDef.eventTypes).keySet()[0] : ''}."
                }
                [
                    resourceReference: comp,
                    description: desc
                ]
            } else {
                [resourceReference: comp,
                 description: "Assembly ${project.name}'s supporting document"
                ]
            }
        }

        def projectJson =  JsonOutput.prettyPrint(JsonOutput.toJson(project))
        log.debug('Built project {}:\n {}', project.name, projectJson)
        Map<String, Object> retVal = [:]

        retVal.path = writeVantiqEntity(VANTIQ_PROJECTS, kameletAssemblyDir, packageName, projectName + JSON_SUFFIX,
            projectJson, false)
        retVal.reference = buildResourceRef(VANTIQ_PROJECTS, packageName, projectName)
        retVal.vailName = packageName
        return retVal
    }

    /**
     * For the routesDocumentString provided, write it out as a Vantiq document as part of the kamelets project.
     *
     * @param kameletAssemblyDir Path the directory used for this kamelet assembly
     * @param packageName String package name we'll use for this assembly
     * @param kamName String name of the kamelet on which we're working
     * @param routesDocumentString String the actual route document as extracted from the Kamelet
     * @return Map<String, Object> containing Path of document written & resourceReference to it when imported
     */
    static Map<String, Object> writeRoutesDocument(Path kameletAssemblyDir,
                                                   String packageName, String kamName, String routesDocumentString) {
        String docName = kamName + YAML_ROUTE_SUFFIX
        Map<String, Object> retVal = [:]
        retVal.path = writeVantiqEntity(VANTIQ_DOCUMENTS, kameletAssemblyDir, packageName,
            docName, routesDocumentString, false)
        retVal.reference = buildResourceRef(VANTIQ_DOCUMENTS, packageName + PACKAGE_SEPARATOR + docName)
        return retVal
    }

    /**
     * For the Overview provided, provide a string representing a complete description.
     *
     * @param isMarkdown boolean indicating if the result should be Markdown (.md) text
     * @param title String the title provided in the kamelet
     * @param baseDescription String the (usually) longer descriptive text as extracted from the Kamelet
     * @param sourceName String the fully qualified source name defined by this assembly
     * @return Map<String, Object> containing Path of document written & resourceReference to it when imported
     */

    static String constructOverviewText(boolean isMarkdown, String title, String baseDescription, String sourceName) {
        StringBuffer overview = new StringBuffer()
        if (isMarkdown) {
            overview.append("# ")
        }
        overview.append("${title} from Camel ${rawCamelVersion}" + OVERVIEW_SOURCE_DISCLAIMER)
        if (sourceName == null) {
            sourceName = "Error: Source Name Not Provided.  Please report to Vantiq."
        }
        if (isMarkdown) {
            overview.append("## ")
        }
        overview.append("Vantiq Sources\n\n")
        overview.append("Vantiq Source defined in this assembly: ${sourceName.replace('_', '\\_')}")
        overview.append("\n\n")
        if (baseDescription != null) {
            if (isMarkdown) {
                overview.append("## ")
            }
            overview.append("Details\n\n")
            overview.append(baseDescription)
            overview.append("\n\n")
        }

        return overview
    }

    /**
     * For the Overview provided, write it out as a Vantiq document as part of the kamelets project.
     *
     * @param kameletAssemblyDir Path the directory used for this kamelet assembly
     * @param packageName String package name we'll use for this assembly
     * @param kamName String name of the kamelet on which we're working
     * @param overview String the (usually) longer descriptive text as extracted from the Kamelet
     * @param title String the title provided in the kamelet
     * @param sourceName String the fully qualified source name defined by this assembly
     * @return Map<String, Object> containing Path of document written & resourceReference to it when imported
     */
    static Map<String, Object> writeOverviewDocument(Path kameletAssemblyDir,
                                                     String packageName, String kamName, String overview,
                                                     String title, String sourceName) {
        String docName = kamName + OVERVIEW_SUFFIX
        Map<String, Object> retVal = [:]
        String titledOverview = constructOverviewText(true, title, overview, sourceName);
        retVal.path = writeVantiqEntity(VANTIQ_DOCUMENTS, kameletAssemblyDir, packageName,
            docName, titledOverview, false)
        retVal.reference = buildResourceRef(VANTIQ_DOCUMENTS, packageName + PACKAGE_SEPARATOR + docName)
        return retVal
    }

    /**
     * If a VantiqNotes file is present in the resource directory, place it in the assembly to be included as a
     * document provided.
     *
     * @param kameletAssemblyDir Path the directory used for this kamelet assembly
     * @param packageName String package name we'll use for this assembly
     * @param kamName String name of the kamelet on which we're working
     * @return Map<String, Object> containing Path of document written & resourceReference to it when imported
     */
    Map<String, Object> writeVantiqNotesDocument(Path kameletAssemblyDir,
                                                     String packageName, String kamName) {
        File notesFile = project.file(RESOURCE_BASE_DIRECTORY + File.separator + VANTIQ_NOTES_DIRECTORY
            + File.separator + kamName + '.md')
        if (!notesFile || !notesFile.exists()) {
            log.trace('No Vantiq Notes document found for {}', notesFile.absolutePath)
            return null
        }
        log.debug('Found VantiqNotes doc for assembly {}: {}', kamName, notesFile.getAbsolutePath())

        String docName = kamName + VANTIQ_NOTES_SUFFIX
        Map<String, Object> retVal = [:]
        // Escape _'s in kamName to avoid strange italics in .md files
        def kamNameForTitle = kamName.replace('_', '\\_')
        String notesTitle =
            "# Using the ${kamNameForTitle} Assembly (Version ${rawCamelVersion})"
        String notesDoc = notesTitle + '\n\n'
        notesFile.readLines().each { aLine ->
            notesDoc += aLine + '\n'
        }
        retVal.path = writeVantiqEntity(VANTIQ_DOCUMENTS, kameletAssemblyDir, packageName,
            docName, notesDoc, false)
        retVal.reference = buildResourceRef(VANTIQ_DOCUMENTS, packageName + PACKAGE_SEPARATOR + docName)
        return retVal
    }

    /**
     * Provide core underlying capability to write a file containing the Vantiq object definition.
     *
     * Handles placing things by package directories or just including the package name in the file name.
     *
     * @param entType String name of the resource type to be written
     * @param kameletAssemblyDir Path file system base at which to write this entity file
     * @param packageName String name of the package for the entity being created
     * @param fileName String base name for the file.  May be "enhanced" depending on the packageIsDirectory setting.
     * @param content String content to write for the entity.
     * @param packageIsDirectory Boolean whether the package should be used to create a directory path (true) or
     *              should be included in the file name (false).  Different component types do things differently.
     * @return Path for the file created.
     */
    static Path writeVantiqEntity(String entType, Path kameletAssemblyDir, String packageName,
                                  String fileName, String content, Boolean packageIsDirectory) {

        List<String> entDirectoryList = [kameletAssemblyDir.toAbsolutePath().toString(), entType]
        if (packageIsDirectory) {
             entDirectoryList += packageName.split(Pattern.quote(PACKAGE_SEPARATOR)).toList()
        }
        log.debug('Creating def for packageIsDirectory: {}, package: {}, fileName: {}',
            packageIsDirectory, packageName, fileName)

        //noinspection GroovyAssignabilityCheck
        Path entDir = Paths.get(*entDirectoryList)
        if (!Files.exists(entDir)) {
            Files.createDirectories(entDir)
        }
        def completeFileName = fileName
        if (!packageIsDirectory) {
            completeFileName = packageName + PACKAGE_SEPARATOR + fileName
        }
        Path entFilePath = Paths.get(entDir.toAbsolutePath().toString(), completeFileName)
        log.info('Creating {} definition at path: {}', entType, entFilePath.toString())
        return Files.writeString(entFilePath, content)
    }

    /**
     * Create simple rules for routing message from source to service event or vice versa
     *
     * @param kameletAssemblyDir Path Directory into which to place results
     * @param packageName String name of package to which the rule belongs
     * @param kamName String Name of the kamelet/assembly for which the rule is being constructed
     * @param sourceName String name of the source about which the rule is being constructed
     * @param serviceName String name of the service involved
     * @param isSink Boolean is the kamelet/assembly a SINK (sends messages to the source)
     * @return Map<String, Object> containing the Path of the rule written and a resource reference to it.
     */
    static Map<String, Object> addRoutingRule(Path kameletAssemblyDir, String packageName, String kamName,
                                              String sourceName, String serviceName, Boolean isSink) {
        Map<String, Object> retVal = [:]
        String ruleName = serviceName + (isSink ? RULE_SINK_NAME_SUFFIX : RULE_SOURCE_NAME_SUFFIX)\

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
                                                                         sourceName: sourceName])
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


    /**
     * Convert the route template into a reified route using the vantiq://server.config style endpoints
     *
     * The route is returned as a list of routes as that's the CAMEL DSL requirement.
     *
     * @param kamName String name of kamelet from which this/these routes originate
     * @param template Map<String, Object> route template from Kamelet YAML file
     * @returns List<Map<String, Object>> Rewritten route template with kamelet sources & sinks replaced with Vantiq
     *                          server references
     */
    List<Map<String, Object>> constructRouteText(String kamName, Map<String, Object> template) {
        processStep(template)
        Map<String, Object> retVal = [:]
        // Must GString --> String lest the yaml dumper get confused.  Easier to just convert to string as opposed to
        // writing a representer
        // Kamelets come generated as templates, so let's preserve that encoding
        retVal[ROUTE_TEMPLATE_TAG] =
            [(ID_ROUTE_TAG): "Route templates from ${kamName}:${packageSafeCamelVersion}".toString()]
        retVal[ROUTE_TEMPLATE_TAG] << template
        // Camel wants a list (even though with kamelets, there's really only one route. The Camel Yaml route parser
        // actually has special handling to recognized that it's reading from a kamelet. But to get along, we'll
        // return a list.
        return [retVal]
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
            if (k == FROM_ROUTE_TAG && stepDef != null) {
                if (stepDef.containsKey(URI_ROUTE_TAG) && stepDef.get(URI_ROUTE_TAG) instanceof String &&
                        ((String) stepDef.get(URI_ROUTE_TAG)) == KAMELET_SOURCE) {
                    stepDef.put(URI_ROUTE_TAG, VANTIQ_SERVER_CONFIG_JSON_STRUCTURED)
                    log.debug('Replaced {} with{}', stepDef.get('uri'), VANTIQ_SERVER_CONFIG_JSON_STRUCTURED)
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
            } else if (k == TO_ROUTE_TAG && KAMELET_SINK == v) {
                log.debug('Replacing value of key {}:{} with \'vantiq://server.config\'', k, v)
                ent.setValue(VANTIQ_SERVER_CONFIG_STRUCTURED)
            } else if (k == TO_ROUTE_TAG && stepDef != null && stepDef.containsKey(URI_ROUTE_TAG)
                    && KAMELET_SINK == stepDef.get(URI_ROUTE_TAG)) {
                stepDef.put(URI_ROUTE_TAG, VANTIQ_SERVER_CONFIG_STRUCTURED)
            }

            // The sections above focus on replacing the kamelet{source, sink} with Vantiq, as appropriate.
            // Below, we make minor enhancements to change kamelet templates into real routes.  They are mostly the
            // same, but there are some parameter definitions that are primarily intended to communicate with the
            // human rather than the code.  For these, we'll quietly delete them.  In theory,  they are supported,
            // but the underlying Camel mechanism just produces errors when they appear in a route definition.  Since
            // they are serving no purpose for us, we'll just quietly delete them.
            //
            // Instances:
            //  - to:
            //      parameters
            //        inBody: resource -- means to include the "resource" as the message body.  This is something the
            //        caller needs to understand, and it's communicated into the Component documentation at the Camel
            //        site.

            if (k == TO_ROUTE_TAG && stepDef != null && stepDef.containsKey(URI_ROUTE_TAG) &&
                    stepDef.containsKey('parameters')) {
                log.debug("Checking for parameters:inBody: {}", stepDef)
                Object oparms = stepDef.get('parameters')
                if (oparms instanceof Map) {
                    Map parms = oparms as Map
                    if (parms.containsKey('inBody')) {
                        // This is a directive the user/caller and isn't really handled well in Camel proper.
                        // This is because the "property" specified is somewhat metaphoric
                        // (e.g., inBody: "resource" -- meaning to send the _resource_ in the body of the
                        // message) and isn't really useful here.  So we'll just quietly remove it.
                        def o = parms.remove('inBody')
                        stepDef.put('parameters', parms)
                        log.debug('Removed "inBody" parameter from "to" step: {}', step)
                    }
                }
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

    // Resource references in projects require the leading /
    static String buildResourceRef(String resourceType, String packageName, String resourceId) {
        String resId = resourceId
        if (packageName) {
            resId = packageName + PACKAGE_SEPARATOR + resId
        }
        return buildResourceRef(resourceType, resId)
    }

    static String buildResourceRef(String resourceType, String resourceId) {
        if (GRANDFATHERED_TYPES.contains(resourceType)) {
            return '/' + "${resourceType}/${resourceId}"
        } else {
            return '/' + VANTIQ_SYSTEM_PREFIX + "${resourceType}/${resourceId}"
        }
    }

    /**
     * Build a resourceReference-like thing for use in exclusion lists.
     *
     * Structurally, these lack the initial '/'.
     *
     * @param resourceType String name of the resource as processed by the exclusion list
     * @param resourceId String name of the resources as processed by the exclusion list
     * @return String reference suitable for inclusion in the project exclusion list
     */
    static String buildExcListReference(String resourceType, String resourceId) {
        return "${resourceType}/${resourceId}"
    }

    // Basic rule to route message sent to a service to the source implementing the kamelet route
    public static final String SINK_RULE_TEMPLATE = '''
package ${packageName}
RULE ${ruleName}
WHEN EVENT OCCURS ON "/services/${packageName + '.' + serviceName}/${serviceName + 'Event'}" AS svcMsg

var srcName = "${sourceName}"

log.debug("Service rule got message {} to forward to source {}", [ svcMsg, srcName ] )

PUBLISH { headers: svcMsg.value.headers, message: svcMsg.value.message } TO SOURCE @srcName
'''

    // Basic rule to route message sent from a source to the service representing the kamelet route
    public static final String SOURCE_RULE_TEMPLATE = '''
package ${packageName}
RULE ${ruleName}
WHEN EVENT OCCURS ON "/sources/${sourceName}" as svcMsg

log.debug("Source ${sourceName} rule got message {} to forward to service  {}", [ svcMsg, 
    "${packageName + '.' + serviceName}/${serviceName + 'Event'}" ])

PUBLISH { headers: svcMsg.value.headers, message: svcMsg.value.message } 
    TO SERVICE EVENT "${packageName + '.' + serviceName}/${serviceName + 'Event'}"
'''

    // Interface description for the generated deployment service.
    public static final List<Map> K8S_DEPLOYMENT_SERVICE_INTERFACE = [
        [
          "name" : "deployToK8s",
          "description" : "Creates a K8sInstallation running the Vantiq Camel Connector implementing the source " +
                "defined by this assembly. The procedure parameters provide the specifics required. Assumes that a " +
                "K8sCluster already exists.",
          "parameters" : [
                [
                    "description" : "The name of the K8sCluster to which to deploy this connector.",
                    "multi" : false,
                    "name" : "clusterName",
                    "required" : true,
                    "type" : "String"
                ],[
                    "description" : "The name of the K8sInstallation to be deployed.",
                    "multi" : false,
                    "name" : "installationName",
                    "required" : true,
                    "type" : "String"
                ],[
                    "description" : "The name of Kubernetes namespace into which to deploy.",
                    "multi" : false,
                    "name" : "k8sNamespace",
                    "required" : false,
                    "type" : "String"
                ],[
                    "description" : "The URL of the Vantiq server with which the connector will interact. " +
                        "Generally, this is the same as what might be used in the browser, but it may be different " +
                        "depending upon the network configuration of the Kubernetes cluster.",
                    "multi" : false,
                    "name" : "targetUrl",
                    "required" : false,
                    "type" : "String"
                ],[
                    "description" : "The access token the connector will use to make the connection to the Vantiq server.",
                    "multi" : false,
                    "name" : "accessToken",
                    "required" : false,
                    "type" : "String"
                ],[
                    "description" : "The limit on the CPU usage for this connector within the Kubernetes cluster.",
                    "multi" : false,
                    "name" : "cpuLimit",
                    "required" : false,
                    "type" : "String"
                ],[
                    "description" : "The limit on the memory usage for this connector within the Kubernetes cluster.",
                    "multi" : false,
                    "name" : "memoryLimit",
                    "required" : false,
                    "type" : "String"
                ],[
                    "description" : "The docker image tag to be used when fetching the image to run",
                    "multi" : false,
                    "name" : "connectorImageTag",
                    "required" : false,
                    "type" : "String"
                ] ]
        ] ]

    // Per-assembly procedure to call for k8s deployment.  It makes use of the common implementation provided by the
    // connector assembly.  The per-assembly's contribution is knowing the source name. Otherwise, it will just
    // gather the set of parameters and pass them along to the common service procedure.

    // (Implementation note:  Note that this must be a pure string rather than Groovy GString.  The syntax for the
    // template placeholders is the same as that used in GStrings, so we have to be careful about how this is used.
    public static final String K8S_DEPLOYMENT_PROCEDURE_TEMPLATE = '''
package ${packageName}

import service ${camConnectorPackage}.${camConnectorDepService}

PROCEDURE ${procedureName}(clusterName String REQUIRED, installationName String REQUIRED, k8sNamespace String,
                targetUrl String, accessToken String,
                cpuLimit String, memoryLimit String, connectorImageTag String)

// Here, we pass the source name along to the doer, passing along any other values passed in...

${camConnectorDepService}.${camConnectorDepProcedure}(clusterName, installationName, k8sNamespace,
                                targetUrl, accessToken, "${sourceName}",
                                cpuLimit, memoryLimit, connectorImageTag) 
'''
}
