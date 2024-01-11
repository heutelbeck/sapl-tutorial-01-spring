package io.sapl.tutorial.data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedList;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.sapl.tutorial.domain.Book;
import io.sapl.tutorial.domain.BookRepository;
import io.sapl.tutorial.domain.LibraryUser;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DemoData implements CommandLineRunner {

    public static final String DEFAULT_RAW_PASSWORD = "password";

    private final BookRepository bookRepository;

    @Override
    public void run(String... args) {
        // @formatter:off
		bookRepository.save(new Book(1L, "Clifford: It's Pool Time!",                                  0, "*Woof*"));
		bookRepository.save(new Book(2L, "The Rescue Mission: (Pokemon: Kalos Reader #1)",             4, "Gotta catch 'em all!"));
		bookRepository.save(new Book(3L, "Dragonlance Chronicles Vol. 1: Dragons of Autumn Twilight",  9, "Some fantasy story."));
		bookRepository.save(new Book(4L, "The Three-Body Problem",                                    14, "Space is scary."));
		// @formatter:on
    }

    private static LocalDate birthdayForAgeInYears(int age) {
        return LocalDate.now().minusYears(age).minusDays(20);
    }

    public static Collection<LibraryUser> users(PasswordEncoder encoder) {
        var users = new LinkedList<LibraryUser>();
        // @formatter:off
        users.add(new LibraryUser("zoe",   birthdayForAgeInYears(17), encoder.encode(DEFAULT_RAW_PASSWORD)));
        users.add(new LibraryUser("bob",   birthdayForAgeInYears(10), encoder.encode(DEFAULT_RAW_PASSWORD)));
        users.add(new LibraryUser("alice", birthdayForAgeInYears(3),  encoder.encode(DEFAULT_RAW_PASSWORD)));
        // @formatter:on
        return users;
    }

}
