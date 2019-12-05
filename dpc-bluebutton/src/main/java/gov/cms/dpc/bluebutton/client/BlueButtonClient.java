package gov.cms.dpc.bluebutton.client;


import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.CapabilityStatement;


public interface BlueButtonClient {

    Bundle requestPatientFromServer(String patientID, DateRangeParam lastUpdated) throws ResourceNotFoundException;

    Bundle requestEOBFromServer(String patientID, DateRangeParam lastUpdated) throws ResourceNotFoundException;

    Bundle requestCoverageFromServer(String patientID, DateRangeParam lastUpdated) throws ResourceNotFoundException;

    Bundle requestNextBundleFromServer(Bundle bundle) throws ResourceNotFoundException;

    CapabilityStatement requestCapabilityStatement() throws ResourceNotFoundException;
}

