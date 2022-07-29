package io.sapl.tutorial.domain;

import java.util.Optional;

import io.sapl.spring.method.metadata.PreEnforce;

public interface BookRepository {
	
	@PreEnforce
	Iterable<Book> findAll();

	@PreEnforce
	Optional<Book> findById(Long id);

	Book save(Book entity);
}