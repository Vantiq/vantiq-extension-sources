/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.LogOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptorMediator;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.retrieve.RetrieveReport;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.matcher.ExactPatternMatcher;
import org.apache.ivy.plugins.namespace.NamespaceTransformer;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class CamelResolver {
    private final String name;
    private final List<URI> repos;
    private final File cache;
    private final File destination;
    final IvySettings ivySettings;
    protected final ChainResolver resolver;
    protected final ResolveOptions resolveOptions;
    protected final RetrieveOptions retrieveOptions;
    
    final Ivy ivy;
    
    private static int unnamedResolverCount = 0;
    private static final String DEFAULT_CONFIGURATION = "default";
    private static final String ANY_CONFIGURATION = "*";
    
    private static final Map<String, String> typeOverrides = Map.of(
            "com.atlassian.sal:sal-api:4.4.2", "jar"
    );
    
    /**
     * Create resolver for necessary artifacts
     *
     * @param name String name of resolver.  Primarily for logging & debugging
     * @param repo URI repos from which to fetch.  If null, use maven central
     * @param cache File Specification of directory for ivy's cache.  If null, take Ivy's defaults.
     * @param destination File Specification of directory to which to copy files
     * @throws IllegalArgumentException for invalid parameters
     */
    
    CamelResolver(String name, URI repo, File cache, File destination) {
        this(name, (repo == null ? Collections.emptyList() : List.of(repo)), cache, destination);
    }
    
    CamelResolver(String name, List<URI> repos, File cache, File destination) {
        if (name != null) {
            this.name = name;
        } else {
            unnamedResolverCount += 1;
            this.name = "UnnamedResolver-" + unnamedResolverCount;
        }
        this.repos = repos;
        this.cache = cache;
        this.destination = destination;
        
        if (destination == null) {
            throw new IllegalArgumentException("The destination parameter cannot be null.");
        }
    
        if (!log.isTraceEnabled()) {
            // Disable voluminous Ivy output that's not really helpful.
            Message.setDefaultLogger(new DefaultMessageLogger(Message.MSG_WARN));
        } else {
            // Turn on voluminous Ivy output in trace case....
            Message.setDefaultLogger(new DefaultMessageLogger(Message.MSG_VERBOSE));
        }
        //creates clear ivy settings
        ivySettings = new IvySettings();
        if (cache != null) {
            ivySettings.setDefaultCache(cache);
        }
        // resolver for configuration of maven repos.  Defaults to including maven central
        resolver = new ChainResolver();
        resolver.setName(name + "::Chain");
        if (repos.size() > 0) {
            for (URI repo : repos) {
                IBiblioResolver aResolver = new IBiblioResolver();
                try {
                    String repoUrl = repo.toURL().toExternalForm();
                    aResolver.setRoot(repoUrl);
                    aResolver.setName(name + "::" + repoUrl);
                } catch (MalformedURLException mue) {
                    throw new IllegalArgumentException("Malformed repos URL: " + repo, mue);
                }
                
                aResolver.setM2compatible(true);
                aResolver.setUsepoms(true);
                resolver.add(aResolver);
            }
        } else {
            IBiblioResolver aResolver = new IBiblioResolver();
    
            aResolver.setM2compatible(true);
            aResolver.setUsepoms(true);
            aResolver.setName(name);
            resolver.add(aResolver);
        }
        //adding maven repos resolver
        ivySettings.addResolver(resolver);
        //set to the default resolver
        ivySettings.setDefaultResolver(resolver.getName());
        
        String[] confs = new String[1];
        confs[0] = DEFAULT_CONFIGURATION;
        resolveOptions = new ResolveOptions();
        resolveOptions.setConfs(confs);
        resolveOptions.setTransitive(true);
        resolveOptions.setDownload(true);
        if (!log.isTraceEnabled()) {
            // Reduce the volume of output from Ivy unless we really need/want it.
            resolveOptions.setLog(LogOptions.LOG_QUIET);
        } else {
            resolveOptions.setLog(LogOptions.LOG_DEFAULT);
        }
        
        retrieveOptions = new RetrieveOptions();
        retrieveOptions.setConfs(confs);
        retrieveOptions.setDestArtifactPattern(destination.getAbsolutePath() +
                                                       "/[artifact]-[revision](-[classifier])" + ".[ext]");
        retrieveOptions.setOverwriteMode(RetrieveOptions.OVERWRITEMODE_NEWER);
        if (!log.isTraceEnabled()) {
            // Reduce the volume of output from Ivy unless we really need/want it.
            retrieveOptions.setLog(LogOptions.LOG_QUIET);
        } else {
            retrieveOptions.setLog(LogOptions.LOG_DEFAULT);
        }
        //creates an Ivy instance with settings
        ivy = Ivy.newInstance(ivySettings);
    }
    
    String identity() {
        String rep = this.repos != null ? this.repos.toString() : "<null>";
        String ourCache = this.cache != null ? this.cache.getAbsolutePath() : "<null>";
        String dest = this.destination != null ? this.destination.getAbsolutePath() : "<null>";
        return MessageFormatter.arrayFormat("CamelResolver {}, repos: {}, cache: {}, destination: {}",
                                new Object[]{this.name, rep, ourCache, dest}).getMessage();
    }
    
    /**
     * Resolve module & copy to destination.
     *
     * @param organization String organization/group for artifact
     * @param name String artifact name
     * @param revision String artifact revision
     * @param purpose String purpose of this resolution.  Used to clarify things in logging.
     * @throws Exception When things go awry
     */
    Collection<File> resolve(String organization, String name, String revision, @NonNull String purpose) throws Exception {
        return resolve(organization, name, revision, null, purpose);
    }
    
    /**
     * Resolve module & copy to destination
     *
     * @param name String artifact name
     * @param revision String artifact revision
     * @param type String type of artifact.  If null, assumes pom file will specify details
     * @param purpose String purpose of this resolution.  Used to clarify things in logging.
     * @throws Exception When things go awry
     */
    Collection<File> resolve(String organization, String name, String revision, String type, @NonNull String purpose)
            throws Exception {
        if (organization == null || name == null || revision == null) {
            throw new IllegalArgumentException("The parameters organization, name, and revision must be " +
                                                       "non-null. (CamelResolver " + identity() + ")");
        }
        
        DefaultModuleDescriptor md =
                DefaultModuleDescriptor.newDefaultInstance(ModuleRevisionId.newInstance(organization,
                                                        name + "-caller", "working"));

        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(md,
                                                                         ModuleRevisionId.newInstance(organization, name, revision),
                                                                         false,
                                                                         false,
                                                                         true);
        dd.addDependencyConfiguration(DEFAULT_CONFIGURATION, ANY_CONFIGURATION);

        if (type != null) {
            Artifact typedArtifact = new DefaultArtifact(ModuleRevisionId.newInstance(organization, name, revision),
                                                         null, name, type, "." + type);
            log.debug(">>>> Specifying artifact for {}:{}", name, type);
            DependencyArtifactDescriptor dad =
                    new DefaultDependencyArtifactDescriptor(dd, name, type, type, null, null);
            dd.addDependencyArtifact(DEFAULT_CONFIGURATION, dad);
            md.addArtifact(DEFAULT_CONFIGURATION, typedArtifact);
        }
        md.addDependency(dd);
        
        // There are some dependencies that don't seem to resolve correctly, at least with our implementation.  To
        // counter this, we track a set of places where we need to override the type provided by the maven repo.
        // This situation typically arises when the artifact in question specifies a packaging attribute for some
        // configurations, and the configuration for which we're locking qualifies.  To manage this, we'll add a
        // DependencyDescriptorMediator (via the lambda below) which, when it sees the dependency specified in the
        // typeOverrides list, will add a DependencyArtifact to force the downloading of th correct type/extension.
        // these adjustments allow us to include having to special case lots of stuff in the kamelet/assembly
        // specifications.  It also will provide the same functions if users configure their own camelConnectors that
        // encounter these situations.
        
        typeOverrides.forEach( (src, newType) -> {
            String[] parts = src.split(":");
            DependencyDescriptorMediator ddm = dependencyDescriptor -> {
                String targetOrg = dependencyDescriptor.getDependencyRevisionId().getOrganisation();
                String targetName = dependencyDescriptor.getDependencyRevisionId().getName();
                String targetRev = dependencyDescriptor.getDependencyRevisionId().getRevision();
                log.trace("CamelConn mediator called with {}:{}:{}", targetOrg, targetName, targetRev);
                // We should get called only with the correct set, but I have seen things behave differently, so
                // better to be safe.  So here we check that the passed-in dependency (target*) match the type
                // overrides for which this DependencyDescriptorMediator is being constructed.
                if (parts[0].equals(targetOrg) && parts[1].equals(targetName) && parts[2].equals(targetRev)) {
                    log.trace(">>>> Specifying artifact for {}:{}:{}:{}", targetOrg, targetName, targetRev,
                              newType);
                    DependencyArtifactDescriptor dad =
                            new DefaultDependencyArtifactDescriptor(dependencyDescriptor, targetName, newType,
                                                                    newType, null, null);
                    if (dependencyDescriptor instanceof DefaultDependencyDescriptor) {
                        // PomModuleDescriptorBuilder.PomDependencyDescriptor is based on
                        // DefaultDependencyDescriptor so this covers that case as well.
                        
                        // In this case, just update the existing dependency
                        ((DefaultDependencyDescriptor)
                                dependencyDescriptor).addDependencyArtifact(ANY_CONFIGURATION, dad);
                    } else {
                        log.error("Unexpected DependencyDescriptor class: {}.  Attempting to reconstruct descriptor " +
                                          "for {}:{}:{}:{}",
                                  dependencyDescriptor.getClass().getName(), targetOrg, targetName, targetRev, newType);
                        NamespaceTransformer identityTransformer = new NamespaceTransformer() {
                            public ModuleRevisionId transform(ModuleRevisionId ourMrid) {
                                return ourMrid;
                            }
                            public boolean isIdentity() {
                                return true;
                            }
                        };
                        DefaultDependencyDescriptor ddRecon =
                                DefaultDependencyDescriptor.transformInstance(
                                        dependencyDescriptor, identityTransformer, false);
                        DependencyArtifactDescriptor dadRecon =
                                new DefaultDependencyArtifactDescriptor(ddRecon, targetName, newType,
                                                                        newType, null, null);

                        ddRecon.addDependencyArtifact(ANY_CONFIGURATION, dadRecon);
                        return ddRecon;
                    }
                    return dependencyDescriptor;
                } else {
                    log.trace("Ignoring mediation for {}:{}:{}", targetOrg, targetName, targetRev);
                }
                return dependencyDescriptor;
            };
            // For each typed override, add a mediator to "fix it up" as required.
            log.debug("Adding mediator for {}:{}", parts[0], parts[1]);
            md.addDependencyDescriptorMediator(new ModuleId(parts[0], parts[1]),
                                               ExactPatternMatcher.INSTANCE, ddm);
        });
        log.info("Resolving required libraries -- {}. This may take a while.", purpose);

        ResolveReport report = ivy.resolve(md, resolveOptions);
        
        if (report.hasError()) {
            // If we get errors, notify our users and fail.
            throw new ResolutionException(identity() + ": Error(s) encountered during resolution: " +
                                                  String.join(", ", report.getAllProblemMessages()));
        }
        if (log.isTraceEnabled()) {
            // do long-winded report only when requested
            ArtifactDownloadReport[] reps = report.getAllArtifactsReports();
            for (ArtifactDownloadReport aRep : reps) {
                Artifact artifact = aRep.getArtifact();
                File lclFile = aRep.getLocalFile();
                String origin;
                if (aRep.getArtifactOrigin() != null) {
                    origin = aRep.getArtifactOrigin().getLocation();
                } else {
                    origin = "<<artifact origin is missing>>";
                }
                log.trace("   --> Cached artifact {}:{}:{} ({}/{}) is now available at {} ({})",
                          artifact.getModuleRevisionId().getOrganisation(),
                          artifact.getName(),
                          artifact.getModuleRevisionId().getRevision(),
                          artifact.getUrl(),
                          origin,
                          (lclFile == null ? "<<local file missing>>" : lclFile.getAbsolutePath()),
                          identity());
            }
        }
        
        RetrieveReport rr = ivy.retrieve(md.getModuleRevisionId(), retrieveOptions);
        // This is the union of files retrieved & those found up-to-date.  We need to include both since our
        // classloader needs to be able to load all the classes, new or otherwise.
        Collection<File> necessaryFiles = rr.getRetrievedFiles();
        for (File f: necessaryFiles) {
            log.trace("Retrieved or up-to-date file: {} ({})", f.getAbsolutePath(), identity());
        }
        log.trace("{} -- Making {} artifacts available to {}", identity(), necessaryFiles.size(), purpose);
        return necessaryFiles;
    }
}
