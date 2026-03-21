package com.nexushub.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, String>>> authFallback() {
        log.warn("Auth service circuit breaker triggered");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Auth service is temporarily unavailable",
                        "code", "AUTH_SERVICE_DOWN",
                        "message", "Please try again in a few moments"
                )));
    }

    @RequestMapping("/ai")
    public Mono<ResponseEntity<Map<String, String>>> aiFallback() {
        log.warn("AI service circuit breaker triggered");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "AI service is temporarily unavailable",
                        "code", "AI_SERVICE_DOWN",
                        "message", "AI features are temporarily unavailable. Your content is safe."
                )));
    }

    @RequestMapping("/default")
    public Mono<ResponseEntity<Map<String, String>>> defaultFallback() {
        log.warn("Service circuit breaker triggered");
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "Service temporarily unavailable",
                        "code", "SERVICE_DOWN",
                        "message", "Please try again in a few moments"
                )));
    }
}
