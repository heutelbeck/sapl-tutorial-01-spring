package io.sapl.tutorial.domain;

import java.util.Optional;

public interface BookRepository {
	Iterable<Book> findAll();
	Optional<Book> findById(Long id);
	Book save(Book entity);
}