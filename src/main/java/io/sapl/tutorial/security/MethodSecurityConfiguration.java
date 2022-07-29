package io.sapl.tutorial.security;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.config.SaplMethodSecurityConfiguration;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;

@Configuration
@EnableGlobalMethodSecurity
public class MethodSecurityConfiguration extends SaplMethodSecurityConfiguration {

	public MethodSecurityConfiguration(ObjectFactory<PolicyDecisionPoint> pdpFactory,
			ObjectFactory<ConstraintEnforcementService> constraintHandlerFactory,
			ObjectFactory<ObjectMapper> objectMapperFactory,
			ObjectFactory<AuthorizationSubscriptionBuilderService> subscriptionBuilderFactory) {
		super(pdpFactory, constraintHandlerFactory, objectMapperFactory, subscriptionBuilderFactory);
	}

}
