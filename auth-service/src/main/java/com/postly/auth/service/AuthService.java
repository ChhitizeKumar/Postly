package com.postly.auth.service;

import com.postly.auth.dto.*;
import com.postly.auth.entity.RefreshToken;
import com.postly.auth.entity.User;
import com.postly.auth.exception.AuthException;
import com.postly.auth.repository.RefreshTokenRepository;
import com.postly.auth.repository.UserRepository;
import com.postly.auth.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository          userRepository;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtProvider             jwtProvider;
    private final AuthenticationManager   authenticationManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ================================================================
    // REGISTER
    // ================================================================
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Check email not already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AuthException.conflict("Email already registered");
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .userName(request.getUsername())
                .provider(User.AuthProvider.LOCAL)
                .role(User.Role.USER)
                .emailVerified(false)
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Publish event to Kafka so other services know a new user joined
        publishUserEvent("user.registered", user, request.getUsername());

        // Generate tokens and return
        return buildAuthResponse(user);
    }

    // ================================================================
    // LOGIN
    // ================================================================
    @Transactional
    public AuthResponse login(LoginRequest request) {

        // Let Spring Security verify email + password
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );
        } catch (BadCredentialsException e) {
            throw AuthException.unauthorized("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> AuthException.notFound("User not found"));

        if (!user.getActive()) {
            throw AuthException.unauthorized("Account is deactivated");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ================================================================
    // REFRESH TOKEN
    // ================================================================
    @Transactional
    public RefreshResponse refresh(RefreshTokenRequest request) {

        RefreshToken stored = refreshTokenRepository
                .findByToken(request.getRefreshToken())
                .orElseThrow(() -> AuthException.unauthorized("Invalid refresh token"));

        if (!stored.isValid()) {
            throw AuthException.unauthorized("Refresh token expired or revoked");
        }

        User user = stored.getUser();

        // Issue a new access token
        String newAccessToken = jwtProvider.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        log.debug("Access token refreshed for: {}", user.getEmail());

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .build();
    }

    // ================================================================
    // LOGOUT
    // ================================================================
    @Transactional
    public void logout(LogoutRequest request) {

        refreshTokenRepository.findByToken(request.getRefreshToken())
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("User logged out: {}", token.getUser().getEmail());
                });
    }

    // ================================================================
    // GET CURRENT USER  (/me endpoint)
    // ================================================================
    public UserResponse getMe(String userId) {

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> AuthException.notFound("User not found"));

        return UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .username(user.getUserName())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .build();
    }

    // ================================================================
    // Private helpers
    // ================================================================

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .user(UserResponse.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .username(user.getUserName())
                        .role(user.getRole().name())
                        .emailVerified(user.getEmailVerified())
                        .build())
                .build();
    }

    private String createRefreshToken(User user) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(token).getToken();
    }

    private void publishUserEvent(String eventType, User user, String username) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", eventType,
                    "userId",    user.getId().toString(),
                    "email",     user.getEmail(),
                    "username",  username != null ? username : "",
                    "role",      user.getRole().name(),
                    "timestamp", Instant.now().toString()
            );
            kafkaTemplate.send("user-events", user.getId().toString(), event);
            log.debug("Published Kafka event: {}", eventType);
        } catch (Exception e) {
            // Don't fail registration if Kafka is down
            log.error("Failed to publish Kafka event: {}", e.getMessage());
        }
    }

    // Cleanup expired tokens every day at midnight
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevokedTokens();
        log.info("Cleaned up expired refresh tokens");
    }
}
