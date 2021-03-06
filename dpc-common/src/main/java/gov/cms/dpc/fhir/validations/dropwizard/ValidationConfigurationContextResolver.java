package gov.cms.dpc.fhir.validations.dropwizard;

import org.glassfish.jersey.server.validation.ValidationConfig;

import javax.inject.Inject;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.ValidatorFactory;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

/**
 * Custom {@link ContextResolver} that allows us to override the default Hibernate/Dropwizard settings in order to support injection for our custom {@link javax.validation.ConstraintValidator}
 * <p>
 * This should be natively supported in Dropwizard 2.0, but until then, we need this.
 */
@Provider
public class ValidationConfigurationContextResolver implements ContextResolver<ValidationConfig> {

    private final ConstraintValidatorFactory constraintValidatorFactory;
    private final ValidatorFactory factory;

    @Inject
    public ValidationConfigurationContextResolver(ConstraintValidatorFactory constraintValidatorFactory, ValidatorFactory factory) {
        this.constraintValidatorFactory = constraintValidatorFactory;
        this.factory = factory;
    }

    @Override
    public ValidationConfig getContext(final Class<?> type) {
        final ValidationConfig config = new ValidationConfig();
        config.messageInterpolator(factory.getMessageInterpolator());
        config.constraintValidatorFactory(constraintValidatorFactory); // custom constraint validator factory
        config.parameterNameProvider(factory.getParameterNameProvider());
        config.traversableResolver(factory.getTraversableResolver());
        return config;
    }
}
