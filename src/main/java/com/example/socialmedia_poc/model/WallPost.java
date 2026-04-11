package com.example.socialmedia_poc.model;

import com.example.socialmedia_poc.config.JpaConverters;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "wall_posts", indexes = {
    @Index(name = "idx_wall_user_batch", columnList = "user_id, batch")
})
public class WallPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JsonIgnore
    private String userId;

    @Column(name = "post_id")
    @JsonProperty("post_id")
    private String postId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String category;

    @Enumerated(EnumType.STRING)
    @JsonProperty("source")
    private PostSource source;

    @Column(name = "meta_config", columnDefinition = "TEXT")
    @Convert(converter = JpaConverters.MetaConfigConverter.class)
    @JsonProperty("meta_config")
    private MetaConfig metaConfig;

    @Column(name = "generation_context", columnDefinition = "TEXT")
    @Convert(converter = JpaConverters.GenerationContextConverter.class)
    @JsonProperty("generation_context")
    private SeedWithMeta.GenerationContext generationContext;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("batch")
    private int batch;

    public enum PostSource {
        SEED,
        GENERATED
    }

    public WallPost() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // Convert from SeedWithMeta
    public static WallPost fromSeed(SeedWithMeta seed, int batch) {
        WallPost post = new WallPost();
        post.setPostId(seed.getSeedId());
        post.setContent(seed.getContent());
        post.setCategory(seed.getCategory());
        post.setSource(PostSource.SEED);
        post.setMetaConfig(seed.getMetaConfig());
        post.setGenerationContext(seed.getGenerationContext());
        post.setBatch(batch);
        return post;
    }

    public static WallPost fromGenerated(SeedWithMeta seed, int batch) {
        WallPost post = fromSeed(seed, batch);
        post.setSource(PostSource.GENERATED);
        return post;
    }

    // Getters and Setters
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public PostSource getSource() { return source; }
    public void setSource(PostSource source) { this.source = source; }

    public MetaConfig getMetaConfig() { return metaConfig; }
    public void setMetaConfig(MetaConfig metaConfig) { this.metaConfig = metaConfig; }

    public SeedWithMeta.GenerationContext getGenerationContext() { return generationContext; }
    public void setGenerationContext(SeedWithMeta.GenerationContext generationContext) { this.generationContext = generationContext; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public int getBatch() { return batch; }
    public void setBatch(int batch) { this.batch = batch; }
}
