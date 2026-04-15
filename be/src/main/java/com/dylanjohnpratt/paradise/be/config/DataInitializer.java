package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.dylanjohnpratt.paradise.be.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Data initializer component that seeds initial users on application startup.
 * Creates an admin user only if the database is empty.
 * Falls back to in-memory user if database connection fails.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InMemoryUserDetailsManager inMemoryUserDetailsManager;

    @Value("${ADMIN_DEFAULT_PASSWORD:}")
    private String adminDefaultPassword;

    public DataInitializer(UserService userService, UserRepository userRepository,
                           PasswordEncoder passwordEncoder, InMemoryUserDetailsManager inMemoryUserDetailsManager) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.inMemoryUserDetailsManager = inMemoryUserDetailsManager;
    }

    @Override
    public void run(String... args) {
        seedUsers();
    }

    private void seedUsers() {
        try {
            long userCount = userRepository.count();

            if (userCount == 0) {
                if (adminDefaultPassword == null || adminDefaultPassword.isBlank()) {
                    throw new IllegalStateException(
                            "ADMIN_DEFAULT_PASSWORD environment variable is required for initial admin user creation. "
                            + "Set it in your .env file or system environment.");
                }
                userService.createUser("admin", adminDefaultPassword, Set.of("ROLE_ADMIN", "ROLE_USER"));
                logger.info("Created initial admin user in database");
            } else {
                logger.info("Database already has {} user(s), skipping seed", userCount);
            }

            logger.info("Data seeding completed");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Database connection failed, falling back to in-memory user: {}", e.getMessage());
            createInMemoryFallbackUser();
        }
    }

    private void createInMemoryFallbackUser() {
        String password = (adminDefaultPassword != null && !adminDefaultPassword.isBlank())
                ? adminDefaultPassword : "adminpass";
        UserDetails fallbackAdmin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(password))
                .roles("ADMIN", "USER")
                .build();

        inMemoryUserDetailsManager.createUser(fallbackAdmin);
        logger.info("Created in-memory fallback admin user");
    }
}
