package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Precise user interest profile built from interaction signals.
 * Recalculated periodically (every N interactions).
 */
public class InterestProfile {

    @JsonProperty("user_id")
    private String userId;

    /** Category → score (0.0 – 1.0). Higher = stronger interest. */
    @JsonProperty("category_scores")
    private Map<String, Double> categoryScores = new HashMap<>();

    /** Category → number of likes. */
    @JsonProperty("category_likes")
    private Map<String, Integer> categoryLikes = new HashMap<>();

    /** Category → number of skips. */
    @JsonProperty("category_skips")
    private Map<String, Integer> categorySkips = new HashMap<>();

    /** Category → total dwell time in ms. */
    @JsonProperty("category_dwell_ms")
    private Map<String, Long> categoryDwellMs = new HashMap<>();

    /** Category → number of interactions. */
    @JsonProperty("category_interaction_count")
    private Map<String, Integer> categoryInteractionCount = new HashMap<>();

    @JsonProperty("preferred_pacing")
    private String preferredPacing = "moderate"; // fast / moderate / slow

    @JsonProperty("content_length_pref")
    private String contentLengthPref = "medium"; // short / medium / long

    @JsonProperty("avg_session_depth")
    private int avgSessionDepth;

    @JsonProperty("total_interactions")
    private int totalInteractions;

    @JsonProperty("total_likes")
    private int totalLikes;

    @JsonProperty("total_skips")
    private int totalSkips;

    @JsonProperty("consecutive_skips")
    private int consecutiveSkips;

    @JsonProperty("last_updated")
    private Instant lastUpdated;

    @JsonProperty("interaction_count_at_last_update")
    private int interactionCountAtLastUpdate;

    public InterestProfile() {
        this.lastUpdated = Instant.now();
    }

    public InterestProfile(String userId) {
        this();
        this.userId = userId;
    }

    // --- Getters / Setters ---

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<String, Double> getCategoryScores() { return categoryScores; }
    public void setCategoryScores(Map<String, Double> categoryScores) { this.categoryScores = categoryScores; }

    public Map<String, Integer> getCategoryLikes() { return categoryLikes; }
    public void setCategoryLikes(Map<String, Integer> categoryLikes) { this.categoryLikes = categoryLikes; }

    public Map<String, Integer> getCategorySkips() { return categorySkips; }
    public void setCategorySkips(Map<String, Integer> categorySkips) { this.categorySkips = categorySkips; }

    public Map<String, Long> getCategoryDwellMs() { return categoryDwellMs; }
    public void setCategoryDwellMs(Map<String, Long> categoryDwellMs) { this.categoryDwellMs = categoryDwellMs; }

    public Map<String, Integer> getCategoryInteractionCount() { return categoryInteractionCount; }
    public void setCategoryInteractionCount(Map<String, Integer> categoryInteractionCount) { this.categoryInteractionCount = categoryInteractionCount; }

    public String getPreferredPacing() { return preferredPacing; }
    public void setPreferredPacing(String preferredPacing) { this.preferredPacing = preferredPacing; }

    public String getContentLengthPref() { return contentLengthPref; }
    public void setContentLengthPref(String contentLengthPref) { this.contentLengthPref = contentLengthPref; }

    public int getAvgSessionDepth() { return avgSessionDepth; }
    public void setAvgSessionDepth(int avgSessionDepth) { this.avgSessionDepth = avgSessionDepth; }

    public int getTotalInteractions() { return totalInteractions; }
    public void setTotalInteractions(int totalInteractions) { this.totalInteractions = totalInteractions; }

    public int getTotalLikes() { return totalLikes; }
    public void setTotalLikes(int totalLikes) { this.totalLikes = totalLikes; }

    public int getTotalSkips() { return totalSkips; }
    public void setTotalSkips(int totalSkips) { this.totalSkips = totalSkips; }

    public int getConsecutiveSkips() { return consecutiveSkips; }
    public void setConsecutiveSkips(int consecutiveSkips) { this.consecutiveSkips = consecutiveSkips; }

    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }

    public int getInteractionCountAtLastUpdate() { return interactionCountAtLastUpdate; }
    public void setInteractionCountAtLastUpdate(int interactionCountAtLastUpdate) { this.interactionCountAtLastUpdate = interactionCountAtLastUpdate; }
}
