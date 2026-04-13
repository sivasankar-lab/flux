package com.example.socialmedia_poc.model;

import com.example.socialmedia_poc.config.JpaConverters;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;

/**
 * A post in the shared pool. Enriched with engagement metrics
 * so it can be scored and recommended to users.
 */
@Entity
@Table(name = "pool_posts", indexes = {
    @Index(name = "idx_pool_category", columnList = "category"),
    @Index(name = "idx_pool_engagement", columnList = "engagement_score"),
    @Index(name = "idx_pool_created", columnList = "created_at")
})
public class PoolPost {

    @Id
    @Column(name = "post_id")
    @JsonProperty("post_id")
    private String postId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String caption;

    private String category;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = JpaConverters.StringListConverter.class)
    private List<String> tags;

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

    // --- Engagement metrics (updated as users interact) ---

    @Column(name = "engagement_score")
    @JsonProperty("engagement_score")
    private double engagementScore;

    @Column(name = "view_count")
    @JsonProperty("view_count")
    private int viewCount;

    @Column(name = "like_count")
    @JsonProperty("like_count")
    private int likeCount;

    @Column(name = "long_read_count")
    @JsonProperty("long_read_count")
    private int longReadCount;

    @Column(name = "skip_count")
    @JsonProperty("skip_count")
    private int skipCount;

    @Column(name = "avg_dwell_ms")
    @JsonProperty("avg_dwell_ms")
    private long avgDwellMs;

    @Column(name = "total_dwell_ms")
    @JsonProperty("total_dwell_ms")
    private long totalDwellMs;

    @Column(name = "generated_for_interest")
    @JsonProperty("generated_for_interest")
    private String generatedForInterest;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    public enum PostSource {
        SEED,
        GENERATED
    }

    public PoolPost() {
        this.createdAt = Instant.now();
    }

    // --- Factory methods ---

    public static PoolPost fromSeedFile(SeedWithMeta seed) {
        PoolPost p = new PoolPost();
        p.setPostId(seed.getSeedId());
        p.setContent(seed.getContent());
        p.setCaption(seed.getCaption());
        p.setCategory(seed.getCategory());
        p.setSource(PostSource.SEED);
        p.setMetaConfig(seed.getMetaConfig());
        p.setGenerationContext(seed.getGenerationContext());
        return p;
    }

    public static PoolPost fromGenerated(SeedWithMeta seed, String forInterest) {
        PoolPost p = fromSeedFile(seed);
        p.setSource(PostSource.GENERATED);
        p.setGeneratedForInterest(forInterest);
        return p;
    }

    /** Recalculate engagement_score from raw counters. */
    public void recalculateEngagement() {
        int totalInteractions = viewCount + likeCount + longReadCount + skipCount;
        if (totalInteractions == 0) {
            this.engagementScore = 0.0;
            return;
        }
        // Weighted formula: likes worth 3, long-reads 2, views 1, skips -1
        double raw = (likeCount * 3.0) + (longReadCount * 2.0) + (viewCount * 1.0) - (skipCount * 1.0);
        // Normalise per interaction so popular old posts don't dominate too much
        this.engagementScore = Math.max(0.0, raw / totalInteractions);
    }

    /** Convert to a WallPost for serving on a user's wall. */
    public WallPost toWallPost(int batch) {
        return toWallPost(batch, null);
    }

    /** Convert to a WallPost for serving on a user's wall, with userId for JPA storage. */
    public WallPost toWallPost(int batch, String userId) {
        WallPost w = new WallPost();
        w.setPostId(this.postId);
        w.setContent(this.content);
        w.setCaption(this.caption);
        w.setCategory(this.category);
        w.setSource(this.source == PostSource.SEED ? WallPost.PostSource.SEED : WallPost.PostSource.GENERATED);
        w.setMetaConfig(this.metaConfig);
        w.setGenerationContext(this.generationContext);
        w.setBatch(batch);
        if (userId != null) w.setUserId(userId);
        return w;
    }

    /** Convert to a SeedWithMeta DTO (for seed API responses). */
    public SeedWithMeta toSeedWithMeta() {
        SeedWithMeta s = new SeedWithMeta();
        s.setSeedId(this.postId);
        s.setContent(this.content);
        s.setCaption(this.caption);
        s.setCategory(this.category);
        s.setMetaConfig(this.metaConfig);
        s.setGenerationContext(this.generationContext);
        s.setLikeCount(this.likeCount);
        s.setViewCount(this.viewCount);
        return s;
    }

    // --- Getters / Setters ---

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public PostSource getSource() { return source; }
    public void setSource(PostSource source) { this.source = source; }

    public MetaConfig getMetaConfig() { return metaConfig; }
    public void setMetaConfig(MetaConfig metaConfig) { this.metaConfig = metaConfig; }

    public SeedWithMeta.GenerationContext getGenerationContext() { return generationContext; }
    public void setGenerationContext(SeedWithMeta.GenerationContext generationContext) { this.generationContext = generationContext; }

    public double getEngagementScore() { return engagementScore; }
    public void setEngagementScore(double engagementScore) { this.engagementScore = engagementScore; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getLongReadCount() { return longReadCount; }
    public void setLongReadCount(int longReadCount) { this.longReadCount = longReadCount; }

    public int getSkipCount() { return skipCount; }
    public void setSkipCount(int skipCount) { this.skipCount = skipCount; }

    public long getAvgDwellMs() { return avgDwellMs; }
    public void setAvgDwellMs(long avgDwellMs) { this.avgDwellMs = avgDwellMs; }

    public long getTotalDwellMs() { return totalDwellMs; }
    public void setTotalDwellMs(long totalDwellMs) { this.totalDwellMs = totalDwellMs; }

    public String getGeneratedForInterest() { return generatedForInterest; }
    public void setGeneratedForInterest(String generatedForInterest) { this.generatedForInterest = generatedForInterest; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
