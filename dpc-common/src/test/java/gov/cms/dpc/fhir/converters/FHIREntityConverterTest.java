package gov.cms.dpc.fhir.converters;

import gov.cms.dpc.common.entities.PatientEntity;
import org.hl7.fhir.dstu3.model.Enumerations;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FHIREntityConverterTest {

    private static FHIREntityConverter converter;

    @BeforeAll
    static void setup() {
        converter = FHIREntityConverter.initialize();
    }

    @Test
    void testPatientConversion() {
        final PatientEntity entity = new PatientEntity();
        entity.setPatientID(UUID.randomUUID());
        entity.setGender(Enumerations.AdministrativeGender.OTHER);

        final Patient patient = converter.toFHIR(Patient.class, entity);

        final PatientEntity converted = converter.fromFHIR(PatientEntity.class, patient);
        assertAll(() -> assertEquals(entity.getPatientID(), converted.getPatientID(), "Should have the same patient ID"),
                () -> assertEquals(entity.getGender(), converted.getGender(), "Should have the same Gender"));
    }
}
