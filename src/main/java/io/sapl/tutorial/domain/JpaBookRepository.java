package io.sapl.tutorial.domain;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
// Attention: here order of interface matters for detecting SAPL annotations. 
public interface JpaBookRepository extends BookRepository, CrudRepository<Book, Long>  {

}