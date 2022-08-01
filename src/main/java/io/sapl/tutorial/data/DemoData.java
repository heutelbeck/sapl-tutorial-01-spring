package io.sapl.tutorial.data;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.sapl.tutorial.domain.Book;
import io.sapl.tutorial.domain.BookRepository;
import io.sapl.tutorial.domain.LibraryUser;
import io.sapl.tutorial.security.LibraryUserDetailsService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DemoData implements CommandLineRunner {

	private final BookRepository bookRepository;
	private final LibraryUserDetailsService userDetailsService;

	@Override
	public void run(String... args) {
		// @formatter:off
		bookRepository.save(new Book(1L, "Clifford: It's Pool Time!",                                  0, "*Woof*"));
		bookRepository.save(new Book(2L, "The Rescue Mission: (Pokemon: Kalos Reader #1)",             4, "Gotta catch 'em all!"));
		bookRepository.save(new Book(3L, "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",  9, "Some fantasy story."));
		bookRepository.save(new Book(4L, "The Three-Body Problem",                                    14, "Space is scary."));

		userDetailsService.load(new LibraryUser("zoe",   birthdayForAgeInYears(17), "{noop}password"));
		userDetailsService.load(new LibraryUser("bob",   birthdayForAgeInYears(10), "{noop}password"));
		userDetailsService.load(new LibraryUser("alice", birthdayForAgeInYears(3),  "{noop}password"));
		// @formatter:on
	}

	private LocalDate birthdayForAgeInYears(int age) {
		return LocalDate.now().minusYears(age).minusDays(20);
	}
}
