package io.sapl.tutorial.domain;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaBookRepository extends CrudRepository<Book, Long>, BookRepository {

}