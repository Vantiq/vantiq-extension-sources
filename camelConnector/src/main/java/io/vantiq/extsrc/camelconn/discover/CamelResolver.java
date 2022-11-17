/*
 * Copyright (c) 2022 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camelconn.discover;

import lombok.extern.slf4j.Slf4j;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyArtifactDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.retrieve.RetrieveReport;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
    
    /**
     * Create resolver for necessary artifacts
     *
     * @param name String name of resolver.  Primarily for logging & debugging
     * @@param repos URI repos from which to fetch.  If null, use maven central
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
                    throw new IllegalArgumentException("Malformed repos URL: " + repo.toString(), mue);
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
    
        retrieveOptions = new RetrieveOptions();
        retrieveOptions.setConfs(confs);
        retrieveOptions.setDestArtifactPattern(destination.getAbsolutePath() +
                                                       "/[artifact]-[revision](-[classifier])" + ".[ext]");
        retrieveOptions.setOverwriteMode(RetrieveOptions.OVERWRITEMODE_NEWER);
        
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
     * @throws Exception When things go awry
     */
    Collection<File> resolve(String organization, String name, String revision) throws Exception {
        return resolve(organization, name, revision, null);
    }
    
    /**
     * Resolve module & copy to destination
     *
     * @param name String artifact name
     * @param revision String artifact revision
     * @param type String type of artifact.  If null, assumes pom file will specify details
     * @throws Exception When things go awry
     */
    Collection<File> resolve(String organization, String name, String revision, String type) throws Exception {
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
        dd.addDependencyConfiguration("default", "default");
        
        if (type != null) {
            // TODO: Figure out why both of these are necessary, but it works when present & not when not.
            Artifact typedArtifact = new DefaultArtifact(ModuleRevisionId.newInstance(organization, name, revision),
                                                         null, name, type, "." + type);
            DependencyArtifactDescriptor dad =
                    new DefaultDependencyArtifactDescriptor(dd, name, type, type, null, null);
            dd.addDependencyArtifact(DEFAULT_CONFIGURATION, dad);
            md.addArtifact(DEFAULT_CONFIGURATION, typedArtifact);
        }
        md.addDependency(dd);
        
        //init resolve report
        ResolveReport report = ivy.resolve(md, resolveOptions);
        
        if (report.hasError()) {
            throw new ResolutionException(identity() + ": Error(s) encountered during resolution: " +
                                                  String.join(", ", report.getAllProblemMessages()));
        }
        
        ArtifactDownloadReport[] reps = report.getAllArtifactsReports();
        for (ArtifactDownloadReport aRep: reps) {
            Artifact artifact = aRep.getArtifact();
            File lclFile = aRep.getLocalFile();
            log.debug("   --> Cached artifact {}:{}:{} is now available at {} ({})",
                      artifact.getModuleRevisionId().getOrganisation(),
                      artifact.getName(),
                      artifact.getModuleRevisionId().getRevision(),
                      lclFile.getAbsolutePath(),
                      identity());
        }
        
        
        RetrieveReport rr = ivy.retrieve(md.getModuleRevisionId(), retrieveOptions);
        Collection<File> copiedFiles = rr.getCopiedFiles();
        for (File f: copiedFiles) {
            log.debug("Retrieved file: {} ({})", f.getAbsolutePath(), identity());
        }
        log.debug("{} -- Retrieve fetched {} artifacts", identity(), copiedFiles.size());
        return copiedFiles;
    }
}
