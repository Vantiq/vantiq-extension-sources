package io.vantiq.extsrc.fhirAssembly;

import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import java.util.Map;

/**
 * Test operations against a FHIR server using fhirConnection assembly
 */
@Slf4j
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestFhirAssemblySearchGet extends TestFhirAssembly {
    
    // Playing this little indirection since the methods are static (Junit Rules) but we want to override.  Copying a
    // bunch of code just to test different situations is not attractive.  JUnit's @BeforeClass will ensure that only
    // one instance of a named method is run, so that suffices for our needs.
    
    @BeforeClass
    public static void setupEnv() {
        performSetup(getAssemblyConfigForTest());
    }
    
    public static Map<String,?> getAssemblyConfigForTest() {
        Map<String, ?> config =  Map.of("fhirServerBaseUrl", FHIR_SERVER,
                                        "defaultSearchHttpMethod", "GET",
                                        "authenticationMechanism", "None");
        log.debug("Returning assembly config of: {}", config);
        return config;
    }
}
