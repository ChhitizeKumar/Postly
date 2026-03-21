package com.nexushub.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ============================================================
// REQUEST DTOs
// ============================================================

class RegisterRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8, max = 72)
    private String password;

    @NotBlank @Size(min = 3, max = 50)
    private String username;
}

class LoginRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String password;
}

class TokenRefreshRequest {
    @NotBlank
    private String refreshToken;
}

class LogoutRequest {
    @NotBlank
    private String refreshToken;
}

class ForgotPasswordRequest {
    @NotBlank @Email
    private String email;
}

class ResetPasswordRequest {
    @NotBlank
    private String token;

    @NotBlank @Size(min = 8, max = 72)
    private String newPassword;
}

// ============================================================
// RESPONSE DTOs
// ============================================================

@Data @Builder @NoArgsConstructor @AllArgsConstructor
class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;        // seconds
    private UserInfo user;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
class UserInfo {
    private String id;
    private String email;
    private String username;
    private String role;
    private boolean emailVerified;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
class TokenRefreshResponse {
    private String accessToken;
    private String tokenType = "Bearer";
    private long expiresIn;
}

@Data @AllArgsConstructor
class MessageResponse {
    private String message;
}

@Data @Builder @NoArgsConstructor @AllArgsConstructor
class TokenValidationResponse {
    private boolean valid;
    private String userId;
    private String email;
    private String role;
}
