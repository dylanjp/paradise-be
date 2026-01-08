package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.AuthResponse;
import com.dylanjohnpratt.paradise.be.dto.LoginRequest;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service for authentication operations.
 * Handles user login and JWT token generation.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthService(AuthenticationManager authenticationManager, 
                       JwtService jwtService, 
                       UserService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    /**
     * Authenticates user credentials and generates a JWT token.
     *
     * @param request the login request containing username and password
     * @return AuthResponse containing the JWT token, username, and roles
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        
        User user = userService.findByUsername(request.username());
        String token = jwtService.generateToken(user);
        
        return new AuthResponse(token, user.getUsername(), user.getRoles());
    }
}
