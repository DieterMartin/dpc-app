package gov.cms.dpc.attribution.resources.v1;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import gov.cms.dpc.attribution.DPCAttributionConfiguration;
import gov.cms.dpc.attribution.jdbi.PatientDAO;
import gov.cms.dpc.attribution.jdbi.ProviderDAO;
import gov.cms.dpc.attribution.jdbi.RelationshipDAO;
import gov.cms.dpc.attribution.jdbi.RosterDAO;
import gov.cms.dpc.attribution.resources.AbstractGroupResource;
import gov.cms.dpc.attribution.utils.RESTUtils;
import gov.cms.dpc.common.entities.AttributionRelationship;
import gov.cms.dpc.common.entities.PatientEntity;
import gov.cms.dpc.common.entities.ProviderEntity;
import gov.cms.dpc.common.entities.RosterEntity;
import gov.cms.dpc.fhir.DPCIdentifierSystem;
import gov.cms.dpc.fhir.FHIRExtractors;
import gov.cms.dpc.fhir.annotations.FHIR;
import gov.cms.dpc.fhir.annotations.FHIRParameter;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.*;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Group;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Api(value = "Group")
public class GroupResource extends AbstractGroupResource {

    private static final Logger logger = LoggerFactory.getLogger(GroupResource.class);
    private static final WebApplicationException NOT_FOUND_EXCEPTION = new WebApplicationException("Cannot find Roster resource", Response.Status.NOT_FOUND);

    private final ProviderDAO providerDAO;
    private final PatientDAO patientDAO;
    private final RosterDAO rosterDAO;
    private final RelationshipDAO relationshipDAO;
    private final DPCAttributionConfiguration config;

    @Inject
    GroupResource(ProviderDAO providerDAO, RosterDAO rosterDAO, PatientDAO patientDAO, RelationshipDAO relationshipDAO, DPCAttributionConfiguration config) {
        this.rosterDAO = rosterDAO;
        this.providerDAO = providerDAO;
        this.patientDAO = patientDAO;
        this.relationshipDAO = relationshipDAO;
        this.config = config;
    }

    @POST
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Create Attribution Roster", notes = "FHIR endpoint to create an Attribution roster (Group resource) associated to the provider listed in the in the Group characteristics.")
    @ApiResponses(value = {
            @ApiResponse(code = 201, message = "Successfully created Roster"),
            @ApiResponse(code = 200, message = "Roster already exists")
    })
    @Override
    public Response createRoster(Group attributionRoster) {
        // Lookup the Provider by NPI
        final String providerNPI = FHIRExtractors.getAttributedNPI(attributionRoster);

        // Check and see if a roster already exists for the provider
        final UUID organizationID = UUID.fromString(FHIRExtractors.getOrganizationID(attributionRoster));
        final List<RosterEntity> entities = this.rosterDAO.findEntities(organizationID, providerNPI, null);
        if (!entities.isEmpty()) {
            return Response.status(Response.Status.OK).entity(entities.get(0).toFHIR()).build();
        }
        final List<ProviderEntity> providers = this.providerDAO.getProviders(null, providerNPI, organizationID);
        if (providers.isEmpty()) {
            throw new WebApplicationException("Unable to find attributable provider", Response.Status.NOT_FOUND);
        }

        final RosterEntity rosterEntity = RosterEntity.fromFHIR(attributionRoster, providers.get(0), generateExpirationTime());

        // Add the first provider
        final RosterEntity persisted = this.rosterDAO.persistEntity(rosterEntity);
        final Group persistedGroup = persisted.toFHIR();

        return Response.status(Response.Status.CREATED).entity(persistedGroup).build();
    }

    @GET
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Search for attribution rosters", notes = "FHIR endpoint to search for Attribution Rosters." +
            "<p> You can search for Groups associated to a given provider (via the Provider NPI) and groups for which a patient is a member of (by the Patient resource ID)")
    @Override
    public Bundle rosterSearch(@ApiParam(value = "Organization ID")
                               @NotEmpty @QueryParam("_tag") String organizationToken,
                               @ApiParam(value = "Provider NPI")
                               @QueryParam(Group.SP_CHARACTERISTIC_VALUE) String providerNPI,
                               @ApiParam(value = "Patient ID")
                               @QueryParam(Group.SP_MEMBER) String patientID) {

        final String providerIDPart;
        if (providerNPI != null) {
            providerIDPart = parseCompositeID(providerNPI).getRight().getIdPart();
        } else {
            providerIDPart = null;
        }

        final Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        final UUID organizationID = RESTUtils.tokenTagToUUID(organizationToken);
        this.rosterDAO.findEntities(organizationID, providerIDPart, patientID)
                .stream()
                .map(RosterEntity::toFHIR)
                .forEach(entity -> bundle.addEntry().setResource(entity));

        bundle.setTotal(bundle.getEntry().size());
        return bundle;
    }

    @GET
    @Path("/{rosterID}/$patients")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Get attributed patient IDs", notes = "FHIR endpoint to retrieve the Patient MBIs for roster entities." +
            "<p> This is an operation optimized for returning only the MBIs for the Patient resources linked to the Roster. " +
            "It returns empty Patient resources with only the MBI added as an identifier.")
    @ApiResponses(@ApiResponse(code = 404, message = "Cannot find attribution roster"))
    @Override
    public Bundle getAttributedPatients(@NotNull @PathParam("rosterID") UUID rosterID, @ApiParam(name = "active", value = "Return only active patients", defaultValue = "false") @QueryParam(value = "active") boolean activeOnly) {
        if (!this.rosterDAO.rosterExists(rosterID)) {
            throw new WebApplicationException(NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND);
        }

        final Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        // We have to do this because Hibernate/Dropwizard gets confused when returning a single type (like String)
        @SuppressWarnings("unchecked") final List<String> patientMBIs = this.patientDAO.fetchPatientMBIByRosterID(rosterID, activeOnly);

        final List<Bundle.BundleEntryComponent> patients = patientMBIs
                .stream()
                .map(mbi -> {
                    // Generate a fake patient, with only the ID set
                    final Patient p = new Patient();
                    p.addIdentifier().setSystem(DPCIdentifierSystem.MBI.getSystem()).setValue(mbi);
                    return new Bundle.BundleEntryComponent().setResource(p);
                })
                .collect(Collectors.toList());

        bundle.setTotal(patientMBIs.size());
        bundle.setEntry(patients);

        return bundle;
    }


    @PUT
    @Path("/{rosterID}")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Update roster", notes = "FHIR endpoint to update the given Group resource with members to add or remove.")
    @ApiResponses(@ApiResponse(code = 404, message = "Cannot find attribution roster"))
    @Override
    public Group replaceRoster(@PathParam("rosterID") UUID rosterID, Group groupUpdate) {
        if (!this.rosterDAO.rosterExists(rosterID)) {
            throw new WebApplicationException(NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND);
        }

        final RosterEntity rosterEntity = new RosterEntity();
        rosterEntity.setId(rosterID);
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Remove all roster relationships
        this.relationshipDAO.removeRosterAttributions(rosterID);

        groupUpdate
                .getMember()
                .stream()
                .map(Group.GroupMemberComponent::getEntity)
                .map(ref -> {
                    final PatientEntity pe = new PatientEntity();
                    pe.setPatientID(UUID.fromString(new IdType(ref.getReference()).getIdPart()));
                    return pe;
                })
                .map(pe -> new AttributionRelationship(rosterEntity, pe))
                .peek(relationship -> relationship.setPeriodEnd(generateExpirationTime()))
                .peek(relationship -> relationship.setPeriodBegin(now))
                .forEach(relationshipDAO::addAttributionRelationship);

        return this.rosterDAO.getEntity(rosterID)
                .orElseThrow(() -> NOT_FOUND_EXCEPTION)
                .toFHIR();
    }

    @POST
    @Path("/{rosterID}/$add")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Add roster members", notes = "FHIR endpoint to update the given Group resource by adding the members included in the supplied Group.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Cannot find attribution roster"),
            @ApiResponse(code = 400, message = "Unable to add patient to roster")
    })
    @Override
    public Group addRosterMembers(@PathParam("rosterID") UUID rosterID, @FHIRParameter Group groupUpdate) {
        if (!this.rosterDAO.rosterExists(rosterID)) {
            throw new WebApplicationException(NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND);
        }

        final RosterEntity rosterEntity = new RosterEntity();
        rosterEntity.setId(rosterID);
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // For each group member, check to see if the patient exists, if not, throw an exception
        // Check to see if they're already rostered, if so, ignore
        groupUpdate
                .getMember()
                .stream()
                .map(Group.GroupMemberComponent::getEntity)
                // Check to see if patient exists, if not, throw an exception
                .map(entity -> {
                    final UUID patientID = UUID.fromString(new IdType(entity.getReference()).getIdPart());
                    return this.patientDAO.getPatient(patientID).orElseThrow(() -> new WebApplicationException(String.format("Cannot find patient with ID %s",
                            patientID.toString()), Response.Status.BAD_REQUEST));
                })
                .map(patient -> {
                    // Check to see if the attribution already exists, if so, re-extend the expiration time
                    final AttributionRelationship relationship = this.relationshipDAO.lookupAttributionRelationship(rosterID, patient.getPatientID())
                            .orElse(new AttributionRelationship(rosterEntity, patient, now));
                    // If the relationship is inactive, then we need to update the period begin for the new membership span
                    if (relationship.isInactive()) {
                        relationship.setPeriodBegin(now);
                    }
                    relationship.setInactive(false);
                    relationship.setPeriodEnd(generateExpirationTime());
                    return relationship;
                })
                .forEach(this.relationshipDAO::addAttributionRelationship);

        return this.rosterDAO.getEntity(rosterID)
                .orElseThrow(() -> NOT_FOUND_EXCEPTION).toFHIR();
    }

    @POST
    @Path("/{rosterID}/$remove")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Remove roster members", notes = "FHIR endpoint to update the given Group resource by removing the members included in the supplied Group.")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Cannot find attribution roster"),
            @ApiResponse(code = 400, message = "Cannot find attribution relationship to remove")
    })
    @Override
    public Group removeRosterMembers(@PathParam("rosterID") UUID rosterID, @FHIRParameter Group groupUpdate) {
        if (!this.rosterDAO.rosterExists(rosterID)) {
            throw new WebApplicationException(NOT_FOUND_EXCEPTION, Response.Status.NOT_FOUND);
        }

        groupUpdate
                .getMember()
                .stream()
                .map(Group.GroupMemberComponent::getEntity)
                .map(entity -> {
                    final PatientEntity patientEntity = new PatientEntity();
                    final UUID patientID = UUID.fromString(new IdType(entity.getReference()).getIdPart());
                    patientEntity.setPatientID(patientID);
                    return this.relationshipDAO.lookupAttributionRelationship(rosterID, patientID);
                })
                .map(rOptional -> rOptional.orElseThrow(() -> new WebApplicationException("Cannot find attribution relationship.", Response.Status.BAD_REQUEST)))
                .peek(relationship -> {
                    relationship.setInactive(true);
                    relationship.setPeriodEnd(OffsetDateTime.now(ZoneOffset.UTC));
                })
                .forEach(this.relationshipDAO::updateAttributionRelationship);

        return this.rosterDAO.getEntity(rosterID)
                .orElseThrow(() -> NOT_FOUND_EXCEPTION).toFHIR();
    }

    @DELETE
    @Path("/{rosterID}")
    @FHIR
    @UnitOfWork
    @ApiOperation(value = "Delete roster", notes = "FHIR Endpoint to delete attribution roster")
    @ApiResponses(@ApiResponse(code = 404, message = "Cannot find attribution roster"))
    @Override
    public Response deleteRoster(@PathParam("rosterID") UUID rosterID) {
        final RosterEntity rosterEntity = this.rosterDAO.getEntity(rosterID)
                .orElseThrow(() -> NOT_FOUND_EXCEPTION);

        this.rosterDAO.delete(rosterEntity);
        return Response.ok().build();
    }


    @Path("/{rosterID}")
    @GET
    @UnitOfWork
    @Timed
    @ExceptionMetered
    @ApiOperation(value = "Get attributed patients", notes = "Returns a list of Patient MBIs that are attributed to the given Provider.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "Internal server error that prevented the service from looking up the attributed patients"),
            @ApiResponse(code = 404, message = "No provider exists with the given NPI")
    })
    @Override
    public Group getRoster(
            @ApiParam(value = "Provider NPI", required = true)
            @PathParam("rosterID") UUID rosterID) {
        logger.debug("API request to retrieve attributed patients for {}", rosterID);

        return this.rosterDAO.getEntity(rosterID)
                .orElseThrow(() -> NOT_FOUND_EXCEPTION)
                .toFHIR();
    }

    private OffsetDateTime generateExpirationTime() {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(config.getExpirationThreshold());
    }

    private static Pair<IdType, IdType> parseCompositeID(String queryParam) {
        final String[] split = queryParam.split("\\$", -1);
        if (split.length != 2) {
            throw new IllegalArgumentException("Cannot parse query param: " + queryParam);
        }
        // Left tag
        final Pair<String, String> leftPair = FHIRExtractors.parseTag(split[0]);
        final IdType leftID = new IdType(leftPair.getLeft(), leftPair.getRight());

        // Right tag
        final Pair<String, String> rightPair = FHIRExtractors.parseTag(split[1]);
        final IdType rightID = new IdType(rightPair.getLeft(), rightPair.getRight());

        return Pair.of(leftID, rightID);
    }

}
