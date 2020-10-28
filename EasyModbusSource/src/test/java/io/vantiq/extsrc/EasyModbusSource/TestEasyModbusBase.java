/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import org.junit.BeforeClass;

public class TestEasyModbusBase {
    static String testIPAddress;
    static int testIPPort;
    static int testSize;
    static String testVantiqServer;
    static String testAuthToken;

    @BeforeClass
    public static void getProps() {
        testIPAddress = System.getProperty("EntConIPAddress");
        String temp = System.getProperty("EntConIPPort");
        if (temp != null) {
            testIPPort = Integer.parseInt(temp);
        }
        temp = System.getProperty("EntBufferSize");
        if (temp != null) {
            testSize = Integer.parseInt(temp);
        }
    }

//    public static String getCommandOutput(String command) {
//        String output = null; // the string to return
//
//        Process process = null;
//        BufferedReader reader = null;
//        InputStreamReader streamReader = null;
//        InputStream stream = null;
//
//        try {
//            process = Runtime.getRuntime().exec(command);
//
//            // Get stream of the console running the command
//            stream = process.getInputStream();
//            streamReader = new InputStreamReader(stream);
//            reader = new BufferedReader(streamReader);
//
//            String currentLine = null; // store current line of output from the cmd
//            StringBuilder commandOutput = new StringBuilder(); // build up the output from cmd
//            while ((currentLine = reader.readLine()) != null) {
//                commandOutput.append(currentLine.toLowerCase() + "\n");
//            }
//
//            int returnCode = process.waitFor();
//            if (returnCode == 0) {
//                output = commandOutput.toString();
//            }
//
//        } catch (IOException e) {
//            System.err.println("Cannot retrieve output of command");
//            System.err.println(e);
//            output = null;
//        } catch (InterruptedException e) {
//            System.err.println("Cannot retrieve output of command");
//            System.err.println(e);
//        } finally {
//            // Close all inputs / readers
//
//            if (stream != null) {
//                try {
//                    stream.close();
//                } catch (IOException e) {
//                    System.err.println("Cannot close stream input! " + e);
//                }
//            }
//            if (streamReader != null) {
//                try {
//                    streamReader.close();
//                } catch (IOException e) {
//                    System.err.println("Cannot close stream input reader! " + e);
//                }
//            }
//            if (reader != null) {
//                try {
//                    reader.close();
//                } catch (IOException e) {
//                    System.err.println("Cannot close reader! " + e);
//                }
//            }
//        }
//        // Return the output from the command - may be null if an error occurred
//        return output;
//    }
//
//    public static boolean isSimulationRunning() {
//
//        return true;
//        // This stuff is not generic & is unnecessary,  If the properties are set but nothing's running,
//        // failure is in order.
//        String program = "EasyModbusServerSimulator".toLowerCase();
//        // or any other process
//        String listOfProcesses = getCommandOutput("tasklist");
//        if (listOfProcesses == null || listOfProcesses.isEmpty()) {
//            System.err.println("Unable to automatically determine if " + program + " is running");
//        } else {
//            if (listOfProcesses.contains(program)) {
//                System.out.println(program + " is running!");
//                return true;
//            } else {
//                System.out.println(program + " is not running!");
//                return false;
//            }
//        } // else: process list can be retrieved
//        return false;
//    }
}
