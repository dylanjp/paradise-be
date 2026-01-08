package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.ChangePasswordRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for user self-service operations.
 * Allows authenticated users to manage their own account.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Changes the current user's password.
     * Requires the current password for verification.
     *
     * @param currentUser the authenticated user from the security context
     * @param request the change password request containing current and new passwords
     * @return success message or error
     */
    @PutMapping("/me/password")
    public ResponseEntity<?> changeOwnPassword(
            @AuthenticationPrincipal User currentUser,
            @RequestBody ChangePasswordRequest request) {
        try {
            userService.changeOwnPassword(
                    currentUser.getUsername(),
                    request.currentPassword(),
                    request.newPassword()
            );
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
