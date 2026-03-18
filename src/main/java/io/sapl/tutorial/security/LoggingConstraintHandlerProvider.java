package io.sapl.tutorial.security;

import org.springframework.stereotype.Service;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
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
	public boolean isResponsible(Value constraint) {
		if (!(constraint instanceof ObjectValue obj)) {
			return false;
		}
		return obj.get("type") instanceof TextValue type && "logAccess".equals(type.value());
	}

	@Override
	public Runnable getHandler(Value constraint) {
		if (constraint instanceof ObjectValue obj && obj.get("message") instanceof TextValue message) {
			return () -> log.info(message.value());
		}
		return () -> log.info("Access logged");
	}

}
