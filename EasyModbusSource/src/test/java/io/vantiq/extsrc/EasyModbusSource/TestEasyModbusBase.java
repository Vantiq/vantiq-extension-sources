/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.BeforeClass;

public class TestEasyModbusBase {
    static String testIPAddress;
    static int testIPPort;
    static int testSize;
    static String testVantiqServer;
    static String testAuthToken;
    static String testSourceName;
    static String testTopicName;
    static String testTypeName;
    static String testProcedureName;
    static String testRuleName;

    @BeforeClass
    public static void getProps() {
        testIPAddress = System.getProperty("EntConIPAddress", "127.0.0.1");
        testIPPort = Integer.parseInt(System.getProperty("EntConIPPort", "502"));
        testSize = Integer.parseInt(System.getProperty("EntBufferSize", "20"));
    }

    public static String getCommandOutput(String command) {
        String output = null; // the string to return

        Process process = null;
        BufferedReader reader = null;
        InputStreamReader streamReader = null;
        InputStream stream = null;

        try {
            process = Runtime.getRuntime().exec(command);

            // Get stream of the console running the command
            stream = process.getInputStream();
            streamReader = new InputStreamReader(stream);
            reader = new BufferedReader(streamReader);

            String currentLine = null; // store current line of output from the cmd
            StringBuilder commandOutput = new StringBuilder(); // build up the output from cmd
            while ((currentLine = reader.readLine()) != null) {
                commandOutput.append(currentLine.toLowerCase() + "\n");
            }

            int returnCode = process.waitFor();
            if (returnCode == 0) {
                output = commandOutput.toString();
            }

        } catch (IOException e) {
            System.err.println("Cannot retrieve output of command");
            System.err.println(e);
            output = null;
        } catch (InterruptedException e) {
            System.err.println("Cannot retrieve output of command");
            System.err.println(e);
        } finally {
            // Close all inputs / readers

            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    System.err.println("Cannot close stream input! " + e);
                }
            }
            if (streamReader != null) {
                try {
                    streamReader.close();
                } catch (IOException e) {
                    System.err.println("Cannot close stream input reader! " + e);
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    System.err.println("Cannot close reader! " + e);
                }
            }
        }
        // Return the output from the command - may be null if an error occured
        return output;
    }

    public static boolean isSimulationRunning() {

        String program = "EasyModbusServerSimulator".toLowerCase();
        ; // or any other process
        String listOfProcesses = getCommandOutput("tasklist");
        if (listOfProcesses == null || listOfProcesses.isEmpty()) {
            System.err.println("Unable to automatically determine if " + program + " is running");
        } else {
            if (listOfProcesses.contains(program)) {
                System.out.println(program + " is running!");
                return true;
            } else {
                System.out.println(program + " is not running!");
                return false;
            }
        } // else: process list can be retrieved
        return false;
    }
}
