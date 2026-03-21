package com.nexushub.contentservice.service;

import com.github.slugify.Slugify;
import com.nexushub.contentservice.dto.CreatePostRequest;
import com.nexushub.contentservice.dto.UpdatePostRequest;
import com.nexushub.contentservice.event.PostEvent;
import com.nexushub.contentservice.model.Post;
import com.nexushub.contentservice.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Slugify slugify;

    /**
     * Get public feed - published posts, paginated
     */
    public Flux<Post> getPublicFeed(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        return postRepository.findByStatusOrderByPublishedAtDesc(Post.PostStatus.PUBLISHED, pageable);
    }

    /**
     * Get personalized feed for a user (posts from people they follow)
     */
    public Flux<Post> getPersonalizedFeed(List<String> followingIds, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        return postRepository.findFeedForFollowing(followingIds, pageable);
    }

    /**
     * Get trending posts (most liked in last 24h)
     */
    public Flux<Post> getTrending(int page, int size) {
        var since = Instant.now().minusSeconds(86400);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "likesCount"));
        return postRepository.findTrending(since, pageable);
    }

    /**
     * Get a single post by slug (public)
     */
    public Mono<Post> getBySlug(String slug) {
        return postRepository.findBySlug(slug)
                .flatMap(post -> {
                    post.setViewsCount(post.getViewsCount() + 1);
                    return postRepository.save(post)
                            .doOnSuccess(saved -> publishEvent("post.viewed", saved));
                });
    }

    /**
     * Get a post by ID
     */
    public Mono<Post> getById(String id) {
        return postRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Post not found: " + id)));
    }

    /**
     * Create a new post (draft or publish immediately)
     */
    public Mono<Post> createPost(CreatePostRequest request, String authorId, String authorUsername) {
        return generateUniqueSlug(request.getTitle())
                .flatMap(slug -> {
                    Post post = Post.builder()
                            .title(request.getTitle())
                            .slug(slug)
                            .content(request.getContent())
                            .excerpt(buildExcerpt(request.getContent()))
                            .authorId(authorId)
                            .authorUsername(authorUsername)
                            .tags(request.getTags())
                            .coverImageUrl(request.getCoverImageUrl())
                            .status(request.isPublishImmediately()
                                    ? Post.PostStatus.PUBLISHED
                                    : Post.PostStatus.DRAFT)
                            .readingTimeMinutes(calculateReadingTime(request.getContent()))
                            .publishedAt(request.isPublishImmediately() ? Instant.now() : null)
                            .build();

                    return postRepository.save(post);
                })
                .doOnSuccess(post -> {
                    if (post.getStatus() == Post.PostStatus.PUBLISHED) {
                        publishEvent("post.created", post);
                        // Request AI auto-tagging asynchronously
                        if (request.isRequestAiTags()) {
                            requestAiAutoTagging(post);
                        }
                    }
                });
    }

    /**
     * Update an existing post
     */
    public Mono<Post> updatePost(String postId, UpdatePostRequest request, String requesterId) {
        return postRepository.findById(postId)
                .switchIfEmpty(Mono.error(new RuntimeException("Post not found")))
                .flatMap(post -> {
                    if (!post.getAuthorId().equals(requesterId)) {
                        return Mono.error(new RuntimeException("Forbidden: not the author"));
                    }

                    boolean wasPublished = post.getStatus() == Post.PostStatus.PUBLISHED;

                    if (request.getTitle() != null) post.setTitle(request.getTitle());
                    if (request.getContent() != null) {
                        post.setContent(request.getContent());
                        post.setExcerpt(buildExcerpt(request.getContent()));
                        post.setReadingTimeMinutes(calculateReadingTime(request.getContent()));
                    }
                    if (request.getTags() != null) post.setTags(request.getTags());
                    if (request.getCoverImageUrl() != null) post.setCoverImageUrl(request.getCoverImageUrl());

                    // Publishing a draft
                    if (!wasPublished && Boolean.TRUE.equals(request.getPublish())) {
                        post.setStatus(Post.PostStatus.PUBLISHED);
                        post.setPublishedAt(Instant.now());
                    }

                    return postRepository.save(post);
                })
                .doOnSuccess(post -> {
                    if (post.getStatus() == Post.PostStatus.PUBLISHED) {
                        publishEvent("post.updated", post);
                    }
                });
    }

    /**
     * Like / unlike a post
     */
    public Mono<Post> toggleLike(String postId, String userId, boolean like) {
        return postRepository.findById(postId)
                .flatMap(post -> {
                    post.setLikesCount(post.getLikesCount() + (like ? 1 : -1));
                    return postRepository.save(post);
                })
                .doOnSuccess(post -> {
                    String eventType = like ? "post.liked" : "post.unliked";
                    publishEvent(eventType, post, userId);
                });
    }

    /**
     * Delete a post
     */
    public Mono<Void> deletePost(String postId, String requesterId) {
        return postRepository.findById(postId)
                .switchIfEmpty(Mono.error(new RuntimeException("Post not found")))
                .flatMap(post -> {
                    if (!post.getAuthorId().equals(requesterId)) {
                        return Mono.error(new RuntimeException("Forbidden"));
                    }
                    return postRepository.delete(post)
                            .doOnSuccess(v -> publishEvent("post.deleted", post));
                });
    }

    /**
     * Search posts by text query
     */
    public Flux<Post> searchPosts(String query, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return postRepository.searchByText(query, pageable);
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private Mono<String> generateUniqueSlug(String title) {
        String baseSlug = slugify.slugify(title);
        return postRepository.existsBySlug(baseSlug)
                .flatMap(exists -> {
                    if (!exists) return Mono.just(baseSlug);
                    String uniqueSlug = baseSlug + "-" + System.currentTimeMillis();
                    return Mono.just(uniqueSlug);
                });
    }

    private String buildExcerpt(String content) {
        if (content == null) return "";
        // Strip markdown, take first 200 chars
        String plain = content.replaceAll("#+\\s", "")
                             .replaceAll("\\*{1,2}(.+?)\\*{1,2}", "$1")
                             .replaceAll("\\[(.+?)]\\(.+?\\)", "$1")
                             .trim();
        return plain.length() > 200 ? plain.substring(0, 200) + "..." : plain;
    }

    private int calculateReadingTime(String content) {
        if (content == null) return 1;
        int wordCount = content.split("\\s+").length;
        return Math.max(1, wordCount / 200); // 200 WPM average reading speed
    }

    private void publishEvent(String eventType, Post post) {
        publishEvent(eventType, post, post.getAuthorId());
    }

    private void publishEvent(String eventType, Post post, String actorId) {
        PostEvent event = PostEvent.builder()
                .eventType(eventType)
                .postId(post.getId())
                .authorId(post.getAuthorId())
                .actorId(actorId)
                .title(post.getTitle())
                .slug(post.getSlug())
                .tags(post.getTags())
                .build();

        kafkaTemplate.send("post-events", post.getId(), event);
        log.info("Published Kafka event: {} for post: {}", eventType, post.getId());
    }

    private void requestAiAutoTagging(Post post) {
        var aiJob = new java.util.HashMap<String, Object>();
        aiJob.put("jobType", "AUTO_TAG");
        aiJob.put("postId", post.getId());
        aiJob.put("userId", post.getAuthorId());
        aiJob.put("content", post.getContent().substring(0, Math.min(2000, post.getContent().length())));
        kafkaTemplate.send("ai-job-requests", post.getId(), aiJob);
    }
}
