package com.nexushub.contentservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;

import java.time.Instant;
import java.util.List;

@Document(collection = "posts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Post {

    @Id
    private String id;

    @TextIndexed(weight = 3)
    private String title;

    @Indexed(unique = true, sparse = true)
    private String slug;

    @TextIndexed
    private String content;

    @TextIndexed(weight = 2)
    private String excerpt;

    private String authorId;
    private String authorUsername;
    private String authorAvatarUrl;

    @Indexed
    private PostStatus status;

    @Indexed
    private List<String> tags;

    private String coverImageUrl;

    @Builder.Default
    private int likesCount = 0;

    @Builder.Default
    private int commentsCount = 0;

    @Builder.Default
    private int viewsCount = 0;

    private int readingTimeMinutes;

    @Builder.Default
    private boolean aiAssisted = false;   // was AI used to write/improve this post?

    private AiMetadata aiMetadata;        // which AI features were used

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant publishedAt;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AiMetadata {
        private boolean improvedWithAi;
        private boolean autoTagged;
        private boolean summarized;
        private String aiModel;
    }

    public enum PostStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }
}
