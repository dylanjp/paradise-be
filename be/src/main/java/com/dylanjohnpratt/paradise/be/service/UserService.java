package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Service for user management operations.
 * Implements UserDetailsService for Spring Security integration.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Loads user by username for Spring Security authentication.
     *
     * @param username the username to search for
     * @return UserDetails for the found user
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Creates a new user with hashed password.
     *
     * @param username the username for the new user
     * @param password the plaintext password (will be hashed)
     * @param roles the roles to assign to the user
     * @return the created user
     * @throws IllegalArgumentException if username already exists
     */
    @Transactional
    public User createUser(String username, String password, Set<String> roles) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        
        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(username, hashedPassword, roles);
        return userRepository.save(user);
    }


    /**
     * Gets all users.
     *
     * @return list of all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Updates the roles for an existing user.
     *
     * @param userId the ID of the user to update
     * @param roles the new set of roles
     * @return the updated user
     * @throws IllegalArgumentException if user is not found
     */
    @Transactional
    public User updateRoles(@NonNull Long userId, Set<String> roles) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        
        user.setRoles(roles);
        return userRepository.save(user);
    }

    /**
     * Resets the password for an existing user (admin operation).
     *
     * @param userId the ID of the user
     * @param newPassword the new plaintext password (will be hashed)
     * @throws IllegalArgumentException if user is not found
     */
    @Transactional
    public void resetPassword(@NonNull Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);
        userRepository.save(user);
    }

    /**
     * Disables a user account.
     *
     * @param userId the ID of the user to disable
     * @throws IllegalArgumentException if user is not found
     */
    @Transactional
    public void disableUser(@NonNull Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
        
        user.setEnabled(false);
        userRepository.save(user);
    }

    /**
     * Deletes a user from the repository.
     *
     * @param userId the ID of the user to delete
     * @throws IllegalArgumentException if user is not found
     */
    @Transactional
    public void deleteUser(@NonNull Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found with id: " + userId);
        }
        userRepository.deleteById(userId);
    }

    /**
     * Changes the password for the current user (self-service).
     * Verifies the current password before updating.
     *
     * @param username the username of the user changing their password
     * @param currentPassword the current plaintext password for verification
     * @param newPassword the new plaintext password (will be hashed)
     * @throws IllegalArgumentException if user is not found or current password is incorrect
     */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(hashedPassword);
        userRepository.save(user);
    }

    /**
     * Finds a user by their ID.
     *
     * @param userId the ID of the user
     * @return the user if found
     * @throws IllegalArgumentException if user is not found
     */
    public User findById(@NonNull Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));
    }

    /**
     * Finds a user by their username.
     *
     * @param username the username to search for
     * @return the user if found
     * @throws IllegalArgumentException if user is not found
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
