package com.nexushub.apigateway.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/forgot-password"
    );

    private final SecretKey secretKey;

    public AuthenticationFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            // Skip auth for public paths
            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            // Extract Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("Missing or invalid Authorization header for path: {}", path);
                return unauthorized(exchange);
            }

            String token = authHeader.substring(BEARER_PREFIX.length());

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                // Forward user info as headers to downstream services
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id", userId)
                        .header("X-User-Email", email)
                        .header("X-User-Role", role)
                        .build();

                log.debug("Authenticated request - userId: {}, path: {}", userId, path);
                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("Expired JWT token for path: {}", path);
                return unauthorized(exchange, "Token expired");
            } catch (JwtException e) {
                log.warn("Invalid JWT token for path: {}: {}", path, e.getMessage());
                return unauthorized(exchange, "Invalid token");
            }
        };
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return unauthorized(exchange, "Unauthorized");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        var body = response.bufferFactory()
                .wrap(("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    public static class Config {
        // Config properties if needed
    }
}
