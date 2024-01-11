package io.sapl.tutorial.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import io.sapl.tutorial.domain.LibraryUser;

public class LibraryUserDetailsService implements UserDetailsService {

    Map<String, LibraryUser> users = new HashMap<>();

    public LibraryUserDetailsService(Collection<LibraryUser> users) {
        users.forEach(this::load);
    }

    public void load(LibraryUser user) {
        users.put(user.getUsername(), user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = users.get(username);
        System.out.println("->"+user);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        return new LibraryUser(user.getUsername(), user.getBirthday(), user.getPassword());
    }

}
