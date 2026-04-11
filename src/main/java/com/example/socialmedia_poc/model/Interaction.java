package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "interactions", indexes = {
    @Index(name = "idx_interactions_user_ts", columnList = "user_id, timestamp"),
    @Index(name = "idx_interactions_user_cat", columnList = "user_id, category"),
    @Index(name = "idx_interactions_type", columnList = "interaction_type")
})
public class Interaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "user_id", nullable = false)
    @JsonProperty("user_id")
    private String userId;

    @Column(name = "seed_id")
    @JsonProperty("seed_id")
    private String seedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    @JsonProperty("interaction_type")
    private InteractionType interactionType;

    @Column(name = "dwell_time_ms")
    @JsonProperty("dwell_time_ms")
    private Long dwellTimeMs;

    private String category;

    private Instant timestamp;

    @Embedded
    @JsonProperty("meta_data")
    private InteractionMetaData metaData;

    public enum InteractionType {
        VIEW, LIKE, SKIP, BOOKMARK, LONG_READ
    }

    public Interaction() {
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSeedId() {
        return seedId;
    }

    public void setSeedId(String seedId) {
        this.seedId = seedId;
    }

    public InteractionType getInteractionType() {
        return interactionType;
    }

    public void setInteractionType(InteractionType interactionType) {
        this.interactionType = interactionType;
    }

    public Long getDwellTimeMs() {
        return dwellTimeMs;
    }

    public void setDwellTimeMs(Long dwellTimeMs) {
        this.dwellTimeMs = dwellTimeMs;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public InteractionMetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(InteractionMetaData metaData) {
        this.metaData = metaData;
    }

    @Embeddable
    public static class InteractionMetaData {
        @Column(name = "meta_intensity")
        private Integer intensity;

        @Column(name = "meta_pacing")
        private String pacing;

        @Column(name = "meta_scroll_depth")
        @JsonProperty("scroll_depth")
        private Double scrollDepth;

        public Integer getIntensity() {
            return intensity;
        }

        public void setIntensity(Integer intensity) {
            this.intensity = intensity;
        }

        public String getPacing() {
            return pacing;
        }

        public void setPacing(String pacing) {
            this.pacing = pacing;
        }

        public Double getScrollDepth() {
            return scrollDepth;
        }

        public void setScrollDepth(Double scrollDepth) {
            this.scrollDepth = scrollDepth;
        }
    }
}
