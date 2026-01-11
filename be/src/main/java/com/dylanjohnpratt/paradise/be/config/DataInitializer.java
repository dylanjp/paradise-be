package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import com.dylanjohnpratt.paradise.be.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Data initializer component that seeds initial users on application startup.
 * Creates admin and regular users if they do not already exist.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserService userService;
    private final UserRepository userRepository;

    public DataInitializer(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        seedUsers();
    }

    private void seedUsers() {
        // Seed admin users with ROLE_ADMIN and ROLE_USER
        //createUserIfNotExists("admin1", "admin1pass", Set.of("ROLE_ADMIN", "ROLE_USER"));
        createUserIfNotExists("admin2", "admin2pass", Set.of("ROLE_ADMIN", "ROLE_USER"));

        // Seed regular users with ROLE_USER only
        //createUserIfNotExists("user1", "user1pass", Set.of("ROLE_USER"));
        //createUserIfNotExists("user2", "user2pass", Set.of("ROLE_USER"));
        //createUserIfNotExists("user3", "user3pass", Set.of("ROLE_USER"));

        logger.info("Data seeding completed");
    }

    private void createUserIfNotExists(String username, String password, Set<String> roles) {
        if (!userRepository.existsByUsername(username)) {
            userService.createUser(username, password, roles);
            logger.info("Created user: {} with roles: {}", username, roles);
        } else {
            logger.debug("User {} already exists, skipping", username);
        }
    }
}
