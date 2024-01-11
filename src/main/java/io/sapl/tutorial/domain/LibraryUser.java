package io.sapl.tutorial.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.userdetails.User;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class LibraryUser extends User {

    @Getter
    private LocalDate birthday;

    public LibraryUser(String username, LocalDate birthday, String password) {
        super(username, password, true, true, true, true, List.of());
        this.birthday = birthday;
    }
}
