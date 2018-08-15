/*
 * Copyright (c) 2018 Vantiq, Inc
 *
 * (Adapted from KeyStoreLoader in Milo project -- Copyright (c) 2017 Keven Herron)
 *
 * Adaptation of the KeyStoreLoader from the Milo project.
 * Project/certificate names changed, fetchCertByName() added.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.html.
 */

package io.vantiq.extsrc.opcua.uaOperations;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.helpers.MessageFormatter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

@Slf4j
class KeyStoreManager {
    public static final String ERROR_PREFIX = "io.vantiq.extsrc.opcua.uaOperations" + "KeyStoreManager";

    private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
        "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private static final String CLIENT_ALIAS = "vantiq-opcua-extension";
    private static final char[] PASSWORD = "vantiqOpcSource".toCharArray();
    private static char[] providedPassword = PASSWORD;

    private X509Certificate clientCertificate;
    private KeyPair clientKeyPair;
    private KeyStore keyStore;
    private File keyStoreFile;

    /**
     * Load keystore from file
     *
     * @param baseDir Directory in which keystore is located
     * @param password Password to gain access to keystore
     * @return KeyStoreManager created by loading the keystore
     * @throws Exception
     */
    KeyStoreManager load(File baseDir, String password) throws Exception {
        providedPassword = password.toCharArray();
        return load(baseDir);
    }

    /**
     * Load keystore from file
     *
     * @param baseDir Directory in which keystore is located
     * @param password Password to gain access to keystore
     * @return KeyStoreManager created by loading the keystore
     * @throws Exception
     */
    KeyStoreManager load(File baseDir, char[] password) throws Exception {
        providedPassword = password;
        return load(baseDir);
    }

    /**
     * Load keystore from file
     *
     * @param baseDir Directory in which keystore is located
     * @return KeyStoreManager created by loading the keystore
     * @throws Exception
     */
    KeyStoreManager load(File baseDir) throws Exception {
        keyStore = KeyStore.getInstance("PKCS12");

        keyStoreFile = baseDir.toPath().resolve("opcuaESKeystore.pfx").toFile();

        log.info("Loading KeyStore at {}", keyStoreFile);

        if (!keyStoreFile.exists()) {
            keyStore.load(null, providedPassword);

            KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

            SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
                .setCommonName("VANTIQ OPC UA Extension Source")
                .setOrganization("vantiq")
                .setOrganizationalUnit("dev")
                .setLocalityName("Walnut Creek")
                .setStateName("CA")
                .setCountryCode("US")
                .setApplicationUri("urn:io:vantiq:extsrc:opcua:client")
                .addDnsName("localhost")
                .addIpAddress("127.0.0.1");

            // Get as many hostnames and IP addresses as we can listed in the certificate.
            for (String hostname : HostnameUtil.getHostnames("0.0.0.0")) {
                if (IP_ADDR_PATTERN.matcher(hostname).matches()) {
                    builder.addIpAddress(hostname);
                } else {
                    builder.addDnsName(hostname);
                }
            }

            X509Certificate certificate = builder.build();

            addCert(CLIENT_ALIAS, keyPair.getPrivate(), certificate);
        } else {
            keyStore.load(new FileInputStream(keyStoreFile), providedPassword);
        }

        Key serverPrivateKey = keyStore.getKey(CLIENT_ALIAS, providedPassword);
        if (serverPrivateKey instanceof PrivateKey) {
            clientCertificate = (X509Certificate) keyStore.getCertificate(CLIENT_ALIAS);
            PublicKey serverPublicKey = clientCertificate.getPublicKey();
            clientKeyPair = new KeyPair(serverPublicKey, (PrivateKey) serverPrivateKey);
        }

        return this;
    }

    /**
     * Add certificate identified by alias to keystore
     * @param alias Alias for the certificate in question
     * @param pKey Private key for the certificate
     * @param cert The certificate to add
     * @throws Exception Based on problems with the certificate, key, or alias.
     */
    void addCert(String alias, PrivateKey pKey, X509Certificate cert) throws Exception {
        keyStore.setKeyEntry(alias, pKey, providedPassword, new X509Certificate[]{cert});
        keyStore.store(new FileOutputStream(keyStoreFile), providedPassword);
    }

    /**
     * Returns the client certificate used in tests
     * @return
     */
    X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    /**
     * Returns the key pair for the client certificate used for tests
     * @return
     */
    KeyPair getClientKeyPair() {
        return clientKeyPair;
    }

    /**
     * Find a certificate in the keyStore identified by the provided alias.
     *
     * @param alias
     * @return
     * @throws OpcExtKeyStoreException
     */
    X509Certificate fetchCertByAlias(String alias) throws OpcExtKeyStoreException {
        try {
            X509Certificate retVal = (X509Certificate) keyStore.getCertificate(alias);
            if (retVal != null) {
                return retVal;
            } else {
                String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".fetchCertByAliasNoSuchCertificate: no X509 certificate for alias '{}'.",
                    new Object[]{alias}).getMessage();
                log.error(errMsg);
                throw new OpcExtKeyStoreException(errMsg);
            }
        }
        catch (OpcExtKeyStoreException oe) {
            throw oe; // Just here to pass this error straight up the chain.
        }
        catch (KeyStoreException e) {
            String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".fetchByAliasError: Unable to fetch certificate for alias '{}'.",
                    new Object[]{alias}).getMessage();

            log.error(errMsg, e);
            throw new OpcExtKeyStoreException(errMsg, e);
        }
    }

    /**
     * Find the private key for the certificate identified by the provided alias.
     *
     * @param alias
     * @return
     * @throws OpcExtKeyStoreException
     */
    PrivateKey fetchPrivateKeyByAlias(String alias) throws OpcExtKeyStoreException {
        try {
            Key certPrivateKey = keyStore.getKey(alias, providedPassword);
            if (certPrivateKey instanceof PrivateKey) {
                return (PrivateKey) certPrivateKey;
            } else {
                String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".fetchPrivateKeyByAliasNoKey: no private key for alias '{}'.",
                        new Object[]{alias}).getMessage();

                log.error(errMsg);
                throw new OpcExtKeyStoreException(errMsg);
            }
        }
        catch (OpcExtKeyStoreException oe) {
            throw oe; // Just here to pass this error straight up the chain.
        }
        catch (Exception e) {
            String errMsg = MessageFormatter.arrayFormat(ERROR_PREFIX + ".fetchPrivateKeyByAliasError: Unable to fetch private key for alias '{}'.",
                    new Object[]{alias}).getMessage();

            log.error(errMsg, e);
            throw new OpcExtKeyStoreException(errMsg, e);
        }
    }
}
