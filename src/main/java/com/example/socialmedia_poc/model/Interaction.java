package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class Interaction {
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("seed_id")
    private String seedId;
    
    @JsonProperty("interaction_type")
    private InteractionType interactionType;
    
    @JsonProperty("dwell_time_ms")
    private Long dwellTimeMs;
    
    private String category;
    
    private Instant timestamp;
    
    @JsonProperty("meta_data")
    private InteractionMetaData metaData;

    public enum InteractionType {
        VIEW, LIKE, SKIP, BOOKMARK, LONG_READ
    }

    public Interaction() {
        this.timestamp = Instant.now();
    }

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

    public static class InteractionMetaData {
        private Integer intensity;
        private String pacing;
        
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
