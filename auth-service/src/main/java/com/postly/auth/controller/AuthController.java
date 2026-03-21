package com.postly.auth.controller;

import com.postly.auth.dto.*;
import com.postly.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ----------------------------------------------------------------
    // POST /api/v1/auth/register
    // Body: { "email": "...", "password": "...", "username": "..." }
    // ----------------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/login
    // Body: { "email": "...", "password": "..." }
    // ----------------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/refresh
    // Body: { "refreshToken": "..." }
    // ----------------------------------------------------------------
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        RefreshResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------------------
    // POST /api/v1/auth/logout
    // Body: { "refreshToken": "..." }
    // ----------------------------------------------------------------
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @Valid @RequestBody LogoutRequest request) {

        authService.logout(request);
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    // ----------------------------------------------------------------
    // GET /api/v1/auth/me
    // Header: Authorization: Bearer <access token>
    // Returns the currently logged in user
    // ----------------------------------------------------------------
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            // @AuthenticationPrincipal gives us the userId from the JWT
            @AuthenticationPrincipal String userId) {

        UserResponse response = authService.getMe(userId);
        return ResponseEntity.ok(response);
    }

    // ----------------------------------------------------------------
    // GET /api/v1/auth/health  (quick sanity check)
    // ----------------------------------------------------------------
    @GetMapping("/health")
    public ResponseEntity<MessageResponse> health() {
        return ResponseEntity.ok(new MessageResponse("Auth service is up!"));
    }
}
