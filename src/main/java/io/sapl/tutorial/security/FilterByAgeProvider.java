package io.sapl.tutorial.security;

import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.spring.constraints.api.FilterPredicateConstraintHandlerProvider;
import io.sapl.tutorial.domain.Book;

@Service
public class FilterByAgeProvider implements FilterPredicateConstraintHandlerProvider {

    @Override
    public boolean isResponsible(JsonNode constraint) {
        return constraint != null && constraint.has("type")
                && "filterBooksByAge".equals(constraint.findValue("type").asText()) && constraint.has("age")
                && constraint.get("age").isNumber();
    }

    @Override
    public Predicate<Object> getHandler(JsonNode constraint) {
        return o -> {
            var age = constraint.get("age").asInt();
            if (o instanceof Book book) {
                return age >= book.getAgeRating();
            } else {
                return true;
            }
        };
    }

}
