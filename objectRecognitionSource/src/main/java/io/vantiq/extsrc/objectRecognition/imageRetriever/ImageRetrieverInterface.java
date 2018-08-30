
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
     * @param source            The ObjectRecognitionCore that will be using the retriever. It is not necessarily
     *                          expected that this will be used
     * @throws Exception        Thrown if the retriever cannot be setup 
     */
    void setupDataRetrieval(Map<String, ?> dataSourceConfig, ObjectRecognitionCore source) throws Exception;
    
    /**
     * Obtain an image's bytes in jpeg format and any other information the implementation deems relevant.
     * @return                              An {@link ImageRetrieverResults} object containing an image in jpeg format
     *                                      and a Map containing any other data the source may need to know. The
     *                                      contents of the Map are implementation dependent.
     * @throws ImageAcquisitionException    Thrown when an image could not be acquired
     * @throws FatalImageException          Thrown when the image retrieval fails in such a way that the retriever
     *                                      cannot recover
     */
    ImageRetrieverResults getImage() throws ImageAcquisitionException;
    
    /**
     * Obtain an image's bytes in jpeg format and any other information the implementation deems relevant 
     * using the options specified in {@code request}.
     * @param request                       The data sent in the Query request.
     * @return                              An {@link ImageRetrieverResults} object containing an image in jpeg format
     *                                      and a Map containing any other data the source may need to know. The
     *                                      contents of the Map are implementation dependent.
     * @throws ImageAcquisitionException    Thrown when an image could not be acquired
     * @throws FatalImageException          Thrown when the image retrieval fails in such a way that the retriever
     *                                      cannot recover
     */
    ImageRetrieverResults getImage(Map<String, ?> request) throws ImageAcquisitionException;
    
    /**
     * Safely close any resources held by the retriever.
     */
    void close();
}
