package gov.cms.dpc.fhir.parameters;

import ca.uhn.fhir.context.FhirContext;
import com.google.inject.Injector;
import gov.cms.dpc.fhir.annotations.FHIRParameter;
import org.glassfish.hk2.api.Factory;
import org.glassfish.jersey.server.model.Parameter;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;
import org.hl7.fhir.instance.model.api.IBaseResource;

import javax.inject.Inject;
import javax.ws.rs.ext.Provider;

/**
 * Custom {@link ValueFactoryProvider} that lets us cleanly map between {@link org.hl7.fhir.dstu3.model.Parameters} and use specified resource types.
 */
@Provider
public class FHIRParamValueFactory implements ValueFactoryProvider {

    private final Injector injector;
    private final FhirContext ctx;

    @Inject
    FHIRParamValueFactory(Injector injector, FhirContext ctx) {
        this.injector = injector;
        this.ctx = ctx;
    }


    @Override
    public Factory<?> getValueFactory(Parameter parameter) {
        if (parameter.getDeclaredAnnotation(FHIRParameter.class) != null) {
            // If the parameter is a resource, pass it off to the resource factory
            if (IBaseResource.class.isAssignableFrom(parameter.getRawType()))
                return new ParamResourceFactory(injector, parameter, ctx.newJsonParser());
        }

        return null;
    }

    @Override
    public PriorityType getPriority() {
        return Priority.NORMAL;
    }
}
