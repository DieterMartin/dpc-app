package gov.cms.dpc.attribution.resources;

import gov.cms.dpc.fhir.annotations.FHIR;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Group;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/Group")
public abstract class AbstractGroupResource {

    protected AbstractGroupResource() {
        // Not used
    }

    @POST
    @FHIR
    public abstract Response submitRoster(Bundle providerBundle);

    @GET
    @Path("/{groupID}")
    public abstract List<String> getAttributedPatients(String groupID);

    @POST
    @Path("/checkUnattributed")
    @FHIR
    public abstract List<String> checkUnattributed(Group attributionGroup);

    @GET
    @Path("/{groupID}/{patientID}")
    public abstract boolean isAttributed(String groupID, String patientID);


    @PUT
    @Path("/{groupID}/{patientID}")
    public abstract void attributePatient(String groupID, String patientID);

    @DELETE
    @Path("/{groupID}/{patientID}")
    public abstract void removeAttribution(String groupID, String patientID);
}
