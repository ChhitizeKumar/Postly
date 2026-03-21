package com.nexushub.aiservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class AnthropicClient {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;

    public AnthropicClient(
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model:claude-sonnet-4-20250514}") String model,
            @Value("${anthropic.max-tokens:1024}") int maxTokens,
            ObjectMapper objectMapper) {

        this.model = model;
        this.maxTokens = maxTokens;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(ANTHROPIC_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }

    /**
     * Single completion - returns the full response as a Mono
     * Use for: auto-tagging, summarization, moderation
     */
    public Mono<String> complete(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage, maxTokens);
    }

    public Mono<String> complete(String systemPrompt, String userMessage, int tokens) {
        ObjectNode body = buildRequestBody(systemPrompt, userMessage, tokens, false);

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response
                        .path("content")
                        .get(0)
                        .path("text")
                        .asText())
                .doOnError(e -> log.error("Anthropic API error: {}", e.getMessage()));
    }

    /**
     * Streaming completion - returns text chunks as Flux<String>
     * Use for: writing assistant (real-time word-by-word streaming to frontend via SSE)
     */
    public Flux<String> streamCompletion(String systemPrompt, String userMessage) {
        ObjectNode body = buildRequestBody(systemPrompt, userMessage, maxTokens, true);

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(data -> !data.equals("[DONE]") && !data.isEmpty())
                .flatMap(data -> {
                    try {
                        JsonNode event = objectMapper.readTree(data);
                        String type = event.path("type").asText();

                        if ("content_block_delta".equals(type)) {
                            return Flux.just(event
                                    .path("delta")
                                    .path("text")
                                    .asText(""));
                        }
                        return Flux.empty();
                    } catch (Exception e) {
                        log.debug("Could not parse SSE data: {}", data);
                        return Flux.empty();
                    }
                })
                .filter(text -> !text.isEmpty())
                .doOnError(e -> log.error("Streaming error: {}", e.getMessage()));
    }

    /**
     * Completion with conversation history (multi-turn)
     */
    public Mono<String> completeWithHistory(String systemPrompt, List<ChatMessage> history) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        for (ChatMessage msg : history) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }

        return webClient.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(response -> response.path("content").get(0).path("text").asText());
    }

    private ObjectNode buildRequestBody(String systemPrompt, String userMessage, int tokens, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", tokens);
        body.put("stream", stream);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("system", systemPrompt);
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);

        return body;
    }

    public record ChatMessage(String role, String content) {}
}
