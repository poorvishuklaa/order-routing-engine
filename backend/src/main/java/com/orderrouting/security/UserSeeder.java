package com.orderrouting.security;

import com.orderrouting.entity.AppUser;
import com.orderrouting.entity.Role;
import com.orderrouting.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the demo users (admin / viewer) if they do not exist. */
@Component
@Order(1)
@RequiredArgsConstructor
public class UserSeeder implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        create("admin", "admin123", Role.ADMIN);
        create("viewer", "viewer123", Role.VIEWER);
    }

    private void create(String username, String password, Role role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new AppUser(username, passwordEncoder.encode(password), role));
        }
    }
}
