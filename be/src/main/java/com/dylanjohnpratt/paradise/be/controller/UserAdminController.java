package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.CreateUserRequest;
import com.dylanjohnpratt.paradise.be.dto.ResetPasswordRequest;
import com.dylanjohnpratt.paradise.be.dto.UpdateRolesRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for admin user management operations.
 * All endpoints require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final Logger log = LoggerFactory.getLogger(UserAdminController.class);

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Gets all users.
     *
     * @return list of all users
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = userService.getAllUsers().stream()
                .map(user -> Map.<String, Object>of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "roles", user.getRoles(),
                        "enabled", user.isEnabled()
                ))
                .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * Creates a new user.
     *
     * @param request the create user request containing username, password, and roles
     * @return the created user
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request,
                                        @AuthenticationPrincipal User currentUser) {
        try {
            User user = userService.createUser(request.username(), request.password(), request.roles());
            log.info("AUDIT admin.createUser actor={} newUsername={} roles={}",
                    currentUser.getUsername(), request.username(), request.roles());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "roles", user.getRoles(),
                    "enabled", user.isEnabled()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Updates the roles for a user.
     *
     * @param id the user ID
     * @param request the update roles request containing the new roles
     * @return the updated user
     */
    @PutMapping("/{id}/roles")
    public ResponseEntity<?> updateRoles(@PathVariable @NonNull Long id, @RequestBody UpdateRolesRequest request,
                                         @AuthenticationPrincipal User currentUser) {
        try {
            User user = userService.updateRoles(id, request.roles());
            log.info("AUDIT admin.updateRoles actor={} targetUserId={} roles={}",
                    currentUser.getUsername(), id, request.roles());
            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "roles", user.getRoles(),
                    "enabled", user.isEnabled()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resets the password for a user (admin operation).
     *
     * @param id the user ID
     * @param request the reset password request containing the new password
     * @return success message
     */
    @PutMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(@PathVariable @NonNull Long id, @RequestBody ResetPasswordRequest request,
                                           @AuthenticationPrincipal User currentUser) {
        try {
            userService.resetPassword(id, request.newPassword());
            log.info("AUDIT admin.resetPassword actor={} targetUserId={}", currentUser.getUsername(), id);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Disables a user account.
     *
     * @param id the user ID
     * @return success message
     */
    @PutMapping("/{id}/disable")
    public ResponseEntity<?> disableUser(@PathVariable @NonNull Long id,
                                         @AuthenticationPrincipal User currentUser) {
        try {
            userService.disableUser(id);
            log.info("AUDIT admin.disableUser actor={} targetUserId={}", currentUser.getUsername(), id);
            return ResponseEntity.ok(Map.of("message", "User disabled successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deletes a user.
     *
     * @param id the user ID
     * @return no content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable @NonNull Long id,
                                        @AuthenticationPrincipal User currentUser) {
        try {
            userService.deleteUser(id);
            log.info("AUDIT admin.deleteUser actor={} targetUserId={}", currentUser.getUsername(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
