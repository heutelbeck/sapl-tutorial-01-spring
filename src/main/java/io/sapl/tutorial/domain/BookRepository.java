package io.sapl.tutorial.domain;

import java.util.List;
import java.util.Optional;

import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;

public interface BookRepository {

    @PreEnforce(subject = "authentication.getPrincipal()",
                action  = "'list books'")
    List<Book> findAll();

    @PostEnforce(subject  = "authentication.getPrincipal()",
                 action   = "'read book'",
                 resource = "returnObject")
    Optional<Book> findById(Long id);

    Book save(Book entity);
}
