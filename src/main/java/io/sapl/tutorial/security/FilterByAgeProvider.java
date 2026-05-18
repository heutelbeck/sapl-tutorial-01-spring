package io.sapl.tutorial.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import io.sapl.tutorial.domain.Book;

@Service
public class FilterByAgeProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "filterBooksByAge";
    private static final int    DEFAULT_PRIORITY = 10;

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintHandlerProvider.constraintIsOfType(constraint, CONSTRAINT_TYPE)) {
            return List.of();
        }
        if (!(constraint instanceof ObjectValue obj) || !(obj.get("age") instanceof NumberValue ageValue)) {
            return List.of();
        }
        final int maxAge = ageValue.value().intValue();
        return SignalType.findIn(supportedSignals, Signal.OutputSignal.class).map(outputSignal -> {
            Mapper<Object> mapper = books -> filterBooks(books, maxAge);
            return List.of(new ScopedConstraintHandler(mapper, outputSignal, DEFAULT_PRIORITY));
        }).orElseGet(List::of);
    }

    private static Object filterBooks(Object value, int maxAge) {
        if (!(value instanceof Iterable<?> iterable)) {
            return value;
        }
        var result = new ArrayList<Book>();
        for (var item : iterable) {
            if (item instanceof Book book && book.getAgeRating() <= maxAge) {
                result.add(book);
            }
        }
        return result;
    }
}
