package io.sapl.tutorial.security;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoggingConstraintHandlerProvider implements RunnableConstraintHandlerProvider {

	@Override
	public Signal getSignal() {
		return Signal.ON_DECISION;
	}

	@Override
	public boolean isResponsible(JsonNode constraint) {
		return constraint != null && constraint.has("type")
				&& "logAccess".equals(constraint.findValue("type").asText());
	}

	@Override
	public Runnable getHandler(JsonNode constraint) {
		return () -> log.info(constraint.findValue("message").asText());

	}

}