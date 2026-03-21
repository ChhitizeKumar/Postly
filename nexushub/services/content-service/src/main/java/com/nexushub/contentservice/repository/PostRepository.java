package com.nexushub.contentservice.repository;

import com.nexushub.contentservice.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
public interface PostRepository extends ReactiveMongoRepository<Post, String> {

    Mono<Post> findBySlug(String slug);

    // Public feed - published posts, newest first
    Flux<Post> findByStatusOrderByPublishedAtDesc(Post.PostStatus status, Pageable pageable);

    // Posts by a specific author
    Flux<Post> findByAuthorIdAndStatusOrderByCreatedAtDesc(
            String authorId, Post.PostStatus status, Pageable pageable);

    // Posts by tag
    Flux<Post> findByTagsContainingAndStatusOrderByPublishedAtDesc(
            String tag, Post.PostStatus status, Pageable pageable);

    // Author's drafts
    Flux<Post> findByAuthorIdAndStatusOrderByUpdatedAtDesc(
            String authorId, Post.PostStatus status, Pageable pageable);

    // Count published posts by author
    Mono<Long> countByAuthorIdAndStatus(String authorId, Post.PostStatus status);

    // Personalized feed - posts from followed authors
    @Query("{ 'authorId': { $in: ?0 }, 'status': 'PUBLISHED' }")
    Flux<Post> findFeedForFollowing(List<String> authorIds, Pageable pageable);

    // Trending - most liked in last N hours
    @Query("{ 'status': 'PUBLISHED', 'publishedAt': { $gte: ?0 } }")
    Flux<Post> findTrending(java.time.Instant since, Pageable pageable);

    // Search (uses MongoDB text index)
    @Query("{ $text: { $search: ?0 }, 'status': 'PUBLISHED' }")
    Flux<Post> searchByText(String query, Pageable pageable);

    Mono<Boolean> existsBySlug(String slug);
}
