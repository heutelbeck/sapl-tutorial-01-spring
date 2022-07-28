package io.sapl.tutorial.rest;

import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.tutorial.domain.Book;
import io.sapl.tutorial.domain.BookRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BookController {

	private final BookRepository repository;

	@GetMapping("/api/books")
	Iterable<Book> findAll() {
		return repository.findAll();
	}

	@GetMapping("/api/books/{id}")
	Optional<Book> findById(@PathVariable Long id) {
		return repository.findById(id);
	}

}