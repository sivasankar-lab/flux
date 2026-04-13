package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Tracks daily trend snapshots for posts.
 * Used to compute trending (velocity), featured, and most-liked feeds.
 */
@Entity
@Table(name = "post_trends", indexes = {
    @Index(name = "idx_trend_date", columnList = "trend_date"),
    @Index(name = "idx_trend_post", columnList = "post_id"),
    @Index(name = "idx_trend_category", columnList = "category"),
    @Index(name = "idx_trend_score", columnList = "trend_score")
})
public class PostTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    @JsonProperty("post_id")
    private String postId;

    @Column(name = "trend_date", nullable = false)
    @JsonProperty("trend_date")
    private LocalDate trendDate;

    private String category;

    @Column(name = "like_count")
    @JsonProperty("like_count")
    private int likeCount;

    @Column(name = "view_count")
    @JsonProperty("view_count")
    private int viewCount;

    @Column(name = "deep_dive_count")
    @JsonProperty("deep_dive_count")
    private int deepDiveCount;

    @Column(name = "engagement_score")
    @JsonProperty("engagement_score")
    private double engagementScore;

    @Column(name = "trend_score")
    @JsonProperty("trend_score")
    private double trendScore;

    @Column(name = "is_featured")
    @JsonProperty("is_featured")
    private boolean featured;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    public PostTrend() {
        this.createdAt = Instant.now();
        this.trendDate = LocalDate.now();
    }

    // Getters & Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public LocalDate getTrendDate() { return trendDate; }
    public void setTrendDate(LocalDate trendDate) { this.trendDate = trendDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public int getDeepDiveCount() { return deepDiveCount; }
    public void setDeepDiveCount(int deepDiveCount) { this.deepDiveCount = deepDiveCount; }

    public double getEngagementScore() { return engagementScore; }
    public void setEngagementScore(double engagementScore) { this.engagementScore = engagementScore; }

    public double getTrendScore() { return trendScore; }
    public void setTrendScore(double trendScore) { this.trendScore = trendScore; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
