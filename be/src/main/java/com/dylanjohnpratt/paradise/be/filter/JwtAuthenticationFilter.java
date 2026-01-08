package com.dylanjohnpratt.paradise.be.filter;

import com.dylanjohnpratt.paradise.be.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter that validates JWT tokens on incoming requests.
 * Extends OncePerRequestFilter to ensure single execution per request.
 * 
 * This filter:
 * - Extracts the Authorization header from incoming requests
 * - Checks for "Bearer " prefix and extracts the token
 * - Validates the token via JwtService
 * - If valid, creates UsernamePasswordAuthenticationToken and sets SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        // Extract the token from the Authorization header
        final String token = extractToken(request);
        
        // If no token or already authenticated, continue the filter chain
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Extract username from token
            final String username = jwtService.extractUsername(token);
            
            if (username != null) {
                // Load user details from database
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                // Validate token against user details
                if (jwtService.isTokenValid(token, userDetails)) {
                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    
                    // Set additional details from the request
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // Set the authentication in the SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired - don't set authentication
            // SecurityContext remains unauthenticated
            logger.debug("JWT validation failed: " + e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the JWT token from the Authorization header.
     * The header must start with "Bearer " prefix.
     *
     * @param request the HTTP request
     * @return the JWT token string, or null if not present or invalid format
     */
    public String extractToken(HttpServletRequest request) {
        final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        return extractTokenFromHeader(authHeader);
    }

    /**
     * Extracts the JWT token from the Authorization header value.
     * The header must start with "Bearer " prefix.
     *
     * @param authHeader the Authorization header value
     * @return the JWT token string, or null if not present or invalid format
     */
    public String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }
}
