package com.nexushub.aiservice.controller;

import com.nexushub.aiservice.dto.*;
import com.nexushub.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * STREAMING endpoint - improve/continue/rewrite text
     * Frontend connects via EventSource, receives SSE chunks
     * MediaType.TEXT_EVENT_STREAM_VALUE is critical for SSE
     */
    @PostMapping(value = "/write/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWritingAssistant(
            @RequestBody WriteAssistRequest request,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Streaming writing assist for user: {}, action: {}", userId, request.getAction());
        return aiService.streamWritingAssistance(request, userId);
    }

    /**
     * Auto-generate tags for a post
     */
    @PostMapping("/tags/generate")
    public Mono<TagsResponse> generateTags(
            @RequestBody GenerateTagsRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return aiService.generateTags(request.getContent(), request.getTitle(), userId);
    }

    /**
     * Summarize a post into a short excerpt
     */
    @PostMapping("/summarize")
    public Mono<SummaryResponse> summarize(
            @RequestBody SummarizeRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return aiService.summarize(request.getContent(), userId);
    }

    /**
     * Upload a document for RAG (PDF, TXT)
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<DocumentUploadResponse> uploadDocument(
            @RequestPart("file") FilePart file,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Document upload for RAG by user: {}, file: {}", userId, file.filename());
        return aiService.processAndEmbedDocument(file, userId);
    }

    /**
     * Ask a question about uploaded documents (RAG)
     */
    @PostMapping("/documents/query")
    public Mono<RagResponse> queryDocuments(
            @RequestBody RagQueryRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return aiService.queryDocuments(request.getQuestion(), request.getDocumentId(), userId);
    }

    /**
     * Get AI job history for a user
     */
    @GetMapping("/jobs")
    public Flux<AiJobResponse> getJobHistory(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return aiService.getJobHistory(userId, page, size);
    }

    /**
     * Get status of a specific async AI job
     */
    @GetMapping("/jobs/{jobId}")
    public Mono<AiJobResponse> getJobStatus(
            @PathVariable String jobId,
            @RequestHeader("X-User-Id") String userId) {
        return aiService.getJobStatus(jobId, userId);
    }
}
