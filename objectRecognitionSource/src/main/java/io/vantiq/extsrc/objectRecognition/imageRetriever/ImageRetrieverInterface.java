
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.imageRetriever;

import java.util.Map;

import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;
import io.vantiq.extsrc.objectRecognition.exception.FatalImageException;
import io.vantiq.extsrc.objectRecognition.exception.ImageAcquisitionException;

public interface ImageRetrieverInterface {
    
    /**
     * Configures the data retriever.
     * @param dataSourceConfig  A map containing the configuration necessary to setup the retriever. This will be the
     *                          'dataSource' object in the source configuration document.
     * @param source            The ObjectRecognitionSource that will be using the retriever. It is not necessarily
     *                          expected that this will be used
     * @throws Exception        Thrown if the retriever cannot be setup 
     */
    void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception;
    
    /**
     * Obtain an image's bytes in jpeg format. For each instance of the retriever, only one of {@link #getImage()} and
     * {@link #getImage(Map)} will be called depending on whether the source is setup for Queries.
     * @return  An image in jpeg format
     * @throws ImageAcquisitionException    Thrown when an image could not be acquired
     * @throws FatalImageException          Thrown when the image retrieval fails in such a way that the retriever
     *                                      cannot recover
     */
    byte[] getImage() throws ImageAcquisitionException;
    
    /**
     * Obtain an image's bytes in jpeg format using the options specified in {@code request}. For each instance of the
     * retriever, only one of {@link #getImage()} and {@link #getImage(Map)} will be called depending on whether
     * the source is setup for Queries.
     * @param request                       The data sent in the Query request.
     * @return                              An image in jpeg format
     * @throws ImageAcquisitionException    Thrown when an image could not be acquired
     * @throws FatalImageException          Thrown when the image retrieval fails in such a way that the retriever
     *                                      cannot recover
     */
    byte[] getImage(Map<String, ?> request) throws ImageAcquisitionException;
    
    /**
     * Safely close any resources obtained by the retriever.
     */
    void close();
}
