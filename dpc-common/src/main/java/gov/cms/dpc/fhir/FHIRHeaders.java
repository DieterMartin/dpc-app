package gov.cms.dpc.fhir;

/**
 * Common constants for FHIR end-points
 */
public class FHIRHeaders {
    private FHIRHeaders() {
        // Private to block creation
    }

    /**
     * Header required for $export operations
     */
    public static final String PREFER_HEADER = "Prefer";

    /**
     * Header value required for $export operations
     */
    public static final String PREFER_RESPOND_ASYNC = "respond-async";
}
