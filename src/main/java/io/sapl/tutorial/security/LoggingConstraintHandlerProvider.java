package io.sapl.tutorial.security;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler.Runner;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.DecisionSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LoggingConstraintHandlerProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "logAccess";
    private static final int    DEFAULT_PRIORITY = 50;

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        var signalOpt = ConstraintHandlerProvider.constraintTypeAndSignal(constraint, CONSTRAINT_TYPE,
                supportedSignals, DecisionSignal.SIGNAL_TYPE);
        if (signalOpt.isEmpty()) {
            return List.of();
        }
        Runner runner = runnerFor(constraint);
        return List.of(new ScopedConstraintHandler(runner, signalOpt.get(), DEFAULT_PRIORITY));
    }

    private static Runner runnerFor(Value constraint) {
        var message = ConstraintHandlerProvider.stringField(constraint, "message").orElse("Access logged");
        return () -> log.info(message);
    }
}
