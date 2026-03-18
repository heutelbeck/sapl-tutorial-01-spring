package io.sapl.tutorial.security;

import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.tutorial.domain.Book;

@Service
public class FilterByAgeProvider implements FilterPredicateConstraintHandlerProvider {

    @Override
    public boolean isResponsible(Value constraint) {
        if (!(constraint instanceof ObjectValue obj)) {
            return false;
        }
        return obj.get("type") instanceof TextValue type
                && "filterBooksByAge".equals(type.value())
                && obj.get("age") instanceof NumberValue;
    }

    @Override
    public Predicate<Object> getHandler(Value constraint) {
        return o -> {
            if (constraint instanceof ObjectValue obj && obj.get("age") instanceof NumberValue age) {
                if (o instanceof Book book) {
                    return age.value().intValue() >= book.getAgeRating();
                }
            }
            return true;
        };
    }

}
