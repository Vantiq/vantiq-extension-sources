/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.opcua.opcUaSource;

import io.vantiq.extjsdk.Utils;
import io.vantiq.extsrc.opcua.uaOperations.OpcConstants;
import io.vantiq.extsrc.opcua.uaOperations.OpcUaESClient;
import org.apache.commons.cli.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class OpcUaServer {

    // Note that this is deprecated in favor of using the extjsdk obtainServerConfig() methods
    // to get things from a standard place.  We will continue support here for backward compatibility.
    
    public static final String LOCAL_CONFIG_FILE_NAME = "sourceconfig.properties";

    public static void main(String[] argv) {
        Options options = new Options();

        Option input = new Option("d", "directory", true, "home directory for this source");
        input.setRequired(false);
        options.addOption(input);

        Option urlOpt = new Option("v", "vantiq", true, "VANTIQ server URL");
        urlOpt.setRequired(false);
        options.addOption(urlOpt);

        Option userOpt = new Option("u", "username", true, "VANTIQ username");
        userOpt.setRequired(false);
        options.addOption(userOpt);

        Option pwOpt = new Option("p", "password", true, "VANTIQ password");
        pwOpt.setRequired(false);
        options.addOption(pwOpt);

        Option tokenOpt = new Option("t", "token", true, "VANTIQ security token");
        tokenOpt.setRequired(false);
        options.addOption(tokenOpt);

        Option sourceNameOpt = new Option("s", "source", true, "VANTIQ source name for this source");
        sourceNameOpt.setRequired(false);
        options.addOption(sourceNameOpt);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, argv);
        } catch (ParseException e) {
            log.error("Error {} parsing command line with arguments: {}", e.getMessage(), argv);
            formatter.printHelp("<opc ua source server> <arguments>", options);
            System.exit(1);
        }

        String homeDir = cmd.getOptionValue("directory");
        if (homeDir == null) {
            homeDir = "storage";
        }
        String user = cmd.getOptionValue("username");
        String vantiqUrl = cmd.getOptionValue("vantiq");
        String pw = cmd.getOptionValue("password");
        String token = cmd.getOptionValue("token");
        String sourceName = cmd.getOptionValue("source");


        Map<String, String> connectInfo = constructConfig(homeDir, sourceName, vantiqUrl, user, pw, token);
        OpcUaESClient.setDefaultStorageDirectory(homeDir);

        OpcUaSource aSource = new OpcUaSource();

        String sourceToUse = connectInfo.get(OpcConstants.VANTIQ_SOURCENAME);

        boolean itWorked = aSource.connectToVantiq(sourceToUse, connectInfo);

        if (aSource != null) {
            aSource.close();
        }
        System.exit(0);
    }

    public static Map<String, String> constructConfig(String location, String sourceOpt, String vantiqUrlOpt, String userOpt, String pwOpt, String tokenOpt) {

        if (location == null) {
            log.error("Missing location of configuration information");
            return null;
        }
        boolean hadError = false;
        Map<String, String> configMap = new HashMap<>();

        File locDir = new File(location);
        if (!locDir.exists()) {
            log.error("Location specified for configuration directory ({}) does not exist.", locDir);
            return null;
        }

        String configFileName = locDir.getAbsolutePath() + File.separator + LOCAL_CONFIG_FILE_NAME;
        InputStream cfr = null;
        Properties props = null;
        
        try {
            File configFile = new File(configFileName);

            if (configFile.exists()) {
                cfr = new FileInputStream(configFileName);

                props = new Properties();
                props.load(cfr);
            } else {
                props = Utils.obtainServerConfig();
            }
            
            // In the effort to commonize the fetching of startup props,
            // we try the common versions first.  When that fails, we walk
            // back through old versions to maintain backward compatibility support.
            
            String url = props.getProperty(OpcConstants.TARGET_SERVER);
            if (url == null) {
                url = props.getProperty(OpcConstants.VANTIQ_URL);
            }
            String username = props.getProperty(OpcConstants.VANTIQ_USERNAME);
            String password = props.getProperty(OpcConstants.VANTIQ_PASSWORD);
            String token = props.getProperty(OpcConstants.VANTIQ_AUTHTOKEN);
            if (token == null) {
                token = props.getProperty(OpcConstants.VANTIQ_TOKEN);
            }
            String sourceName = props.getProperty(OpcConstants.VANTIQ_SOURCE);
            if (sourceName == null) {
                sourceName = props.getProperty(OpcConstants.VANTIQ_SOURCES);
                if (sourceName == null) {
                    sourceName = props.getProperty(OpcConstants.VANTIQ_SOURCENAME);
                }
            }

            if (url != null) {
                configMap.put(OpcConstants.VANTIQ_URL, url);
            }
            if (username != null) {
                configMap.put(OpcConstants.VANTIQ_USERNAME, username);
            }
            if (password != null) {
                configMap.put(OpcConstants.VANTIQ_PASSWORD, password);
            }
            if (token != null) {
                configMap.put(OpcConstants.VANTIQ_TOKEN, token);
            }
            if (sourceName != null) {
                configMap.put(OpcConstants.VANTIQ_SOURCENAME, sourceName);
            }
        } catch (FileNotFoundException e) {
            // This is OK as we allow command line options
        } catch (IOException e) {
            log.error("Config file ({}) was not readable: {}", configFileName, e.getMessage());
            hadError = true;
        } finally {
            if (cfr != null) {
                try {
                    cfr.close();
                } catch (IOException e) {
                    log.error("Unable to close config file: {} due to error: {}", configFileName, e.getMessage());
                    log.error(e.getStackTrace().toString());
                    hadError = true;
                }
            }
        }
        if (hadError) {
            configMap = null;
        } else {

            // Do these last as command line arguments override the properties file

            if (vantiqUrlOpt != null) {
                configMap.put(OpcConstants.VANTIQ_URL, vantiqUrlOpt);
            }
            if (userOpt != null) {
                configMap.put(OpcConstants.VANTIQ_USERNAME, userOpt);
            }
            if (pwOpt != null) {
                configMap.put(OpcConstants.VANTIQ_PASSWORD, pwOpt);
            }
            if (sourceOpt != null) {
                configMap.put(OpcConstants.VANTIQ_SOURCENAME, sourceOpt);
            }
            if (tokenOpt != null) {
                configMap.put(OpcConstants.VANTIQ_TOKEN, tokenOpt);
            }

        }
        return configMap;
    }
}
