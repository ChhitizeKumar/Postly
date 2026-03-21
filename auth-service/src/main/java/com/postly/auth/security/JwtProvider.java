package com.postly.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiryMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs) {

        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    // -------------------------------------------------------
    // Generate access token
    // Contains: userId, email, role — everything downstream
    // services need, so they don't have to call auth service
    // -------------------------------------------------------
    public String generateAccessToken(UUID userId, String email, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .claims(Map.of(
                        "email", email,
                        "role",  role
                ))
                .signWith(key)
                .compact();
    }

    // -------------------------------------------------------
    // Validate and parse a token
    // -------------------------------------------------------
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public String extractUserId(String token) {
        return parseToken(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}
