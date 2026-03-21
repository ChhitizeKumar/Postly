package com.nexushub.aiservice.service;

import com.nexushub.aiservice.client.AnthropicClient;
import com.nexushub.aiservice.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AnthropicClient anthropicClient;
    private final EmbeddingService embeddingService;
    private final AiJobRepository aiJobRepository;

    // =========================================================
    // Writing Assistant - STREAMING
    // =========================================================

    public Flux<String> streamWritingAssistance(WriteAssistRequest request, String userId) {
        String systemPrompt = buildWritingSystemPrompt(request.getAction());
        String userMessage  = buildWritingUserMessage(request);

        return anthropicClient.streamCompletion(systemPrompt, userMessage)
                .doOnComplete(() -> log.info("Streaming complete for user: {}", userId));
    }

    private String buildWritingSystemPrompt(String action) {
        return switch (action.toUpperCase()) {
            case "IMPROVE" -> """
                You are an expert writing editor. Your task is to improve the given text
                while preserving the author's voice and intent. Make it clearer, more engaging,
                and better structured. Return ONLY the improved text, no commentary.
                """;
            case "EXPAND" -> """
                You are a skilled writer. Expand the given text with more detail, examples,
                and depth. Maintain the original tone and style. Return ONLY the expanded text.
                """;
            case "SHORTEN" -> """
                You are a concise writer. Shorten the given text while preserving all key
                information and the author's voice. Return ONLY the shortened text.
                """;
            case "CONTINUE" -> """
                You are a creative writer. Continue writing from where the given text ends,
                maintaining the exact same style, tone, and voice. Return ONLY the continuation.
                """;
            case "FIX_GRAMMAR" -> """
                You are a grammar expert. Fix all grammatical errors, spelling mistakes, and
                punctuation issues in the given text. Preserve the meaning and style exactly.
                Return ONLY the corrected text.
                """;
            default -> """
                You are a helpful writing assistant. Assist with the given text as requested.
                Return ONLY the result, no commentary.
                """;
        };
    }

    private String buildWritingUserMessage(WriteAssistRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getContext() != null && !request.getContext().isBlank()) {
            sb.append("Context (full article so far):\n").append(request.getContext()).append("\n\n");
        }
        sb.append("Text to work on:\n").append(request.getText());
        if (request.getInstruction() != null && !request.getInstruction().isBlank()) {
            sb.append("\n\nAdditional instruction: ").append(request.getInstruction());
        }
        return sb.toString();
    }

    // =========================================================
    // Auto-Tagging
    // =========================================================

    public Mono<TagsResponse> generateTags(String content, String title, String userId) {
        String systemPrompt = """
                You are a content categorization expert for a developer/tech blogging platform.
                Analyze the given article and suggest 3-7 relevant tags from common tech topics.
                
                Tags should be: lowercase, hyphenated (e.g. spring-boot), specific, and useful for discovery.
                
                Common tags: javascript, typescript, java, python, spring-boot, react, nodejs, docker,
                kubernetes, microservices, ai, machine-learning, devops, aws, database, kafka, redis,
                web-development, backend, frontend, tutorial, best-practices
                
                Respond ONLY with a JSON array of strings, nothing else.
                Example: ["spring-boot", "java", "microservices", "kafka"]
                """;

        String userMessage = "Title: " + title + "\n\nContent:\n" + content.substring(0, Math.min(3000, content.length()));

        return anthropicClient.complete(systemPrompt, userMessage)
                .map(response -> {
                    // Parse JSON array response
                    try {
                        String cleaned = response.trim()
                                .replaceAll("^```json\\n?", "")
                                .replaceAll("\\n?```$", "");
                        // Simple parsing - in production use ObjectMapper
                        List<String> tags = List.of(cleaned
                                .replaceAll("[\\[\\]\"]", "")
                                .split(",\\s*"));
                        return new TagsResponse(tags, "claude-sonnet-4-20250514");
                    } catch (Exception e) {
                        log.error("Failed to parse tags response: {}", response);
                        return new TagsResponse(List.of(), "claude-sonnet-4-20250514");
                    }
                });
    }

    // =========================================================
    // Summarization
    // =========================================================

    public Mono<SummaryResponse> summarize(String content, String userId) {
        String systemPrompt = """
                You are an expert at writing concise, engaging article summaries.
                Create a 2-3 sentence summary that captures the key points and would make
                someone want to read the full article. No fluff, no filler.
                Return ONLY the summary text.
                """;

        return anthropicClient.complete(systemPrompt, "Summarize this:\n\n" + content)
                .map(summary -> new SummaryResponse(summary.trim()));
    }

    // =========================================================
    // RAG - Document Q&A
    // =========================================================

    public Mono<DocumentUploadResponse> processAndEmbedDocument(FilePart file, String userId) {
        return embeddingService.processDocument(file, userId)
                .map(docId -> new DocumentUploadResponse(docId, file.filename(), "Document processed and ready for Q&A"));
    }

    public Mono<RagResponse> queryDocuments(String question, String documentId, String userId) {
        // 1. Embed the question
        return embeddingService.embedText(question)
                .flatMap(questionEmbedding ->
                        // 2. Find most relevant chunks via cosine similarity
                        embeddingService.findSimilarChunks(questionEmbedding, documentId, userId, 5))
                .flatMap(relevantChunks -> {
                    if (relevantChunks.isEmpty()) {
                        return Mono.just(new RagResponse("I couldn't find relevant information in the document to answer your question.", List.of()));
                    }

                    // 3. Build RAG prompt with context
                    String context = String.join("\n\n---\n\n", relevantChunks.stream()
                            .map(chunk -> chunk.chunkText())
                            .toList());

                    String systemPrompt = """
                            You are a helpful assistant that answers questions based ONLY on the provided document context.
                            If the answer is not in the context, say "I don't have enough information in the document to answer that."
                            Be concise and cite specific parts of the document when possible.
                            """;

                    String userMessage = "Document context:\n" + context + "\n\nQuestion: " + question;

                    return anthropicClient.complete(systemPrompt, userMessage)
                            .map(answer -> new RagResponse(answer, relevantChunks.stream()
                                    .map(c -> c.chunkText().substring(0, Math.min(100, c.chunkText().length())))
                                    .toList()));
                });
    }

    // =========================================================
    // Job history
    // =========================================================

    public Flux<AiJobResponse> getJobHistory(String userId, int page, int size) {
        return aiJobRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(AiJobResponse::from);
    }

    public Mono<AiJobResponse> getJobStatus(String jobId, String userId) {
        return aiJobRepository.findByIdAndUserId(jobId, userId)
                .map(AiJobResponse::from)
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found")));
    }
}
