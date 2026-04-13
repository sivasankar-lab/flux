package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SeedWithMeta {
    @JsonProperty("seed_id")
    private String seedId;
    
    private String content;
    private String caption;
    private String category;
    
    @JsonProperty("meta_config")
    private MetaConfig metaConfig;
    
    @JsonProperty("generation_context")
    private GenerationContext generationContext;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("view_count")
    private int viewCount;

    public SeedWithMeta() {
    }

    public SeedWithMeta(String seedId, String content, String category, MetaConfig metaConfig) {
        this.seedId = seedId;
        this.content = content;
        this.category = category;
        this.metaConfig = metaConfig;
    }

    public String getSeedId() {
        return seedId;
    }

    public void setSeedId(String seedId) {
        this.seedId = seedId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public MetaConfig getMetaConfig() {
        return metaConfig;
    }

    public void setMetaConfig(MetaConfig metaConfig) {
        this.metaConfig = metaConfig;
    }

    public GenerationContext getGenerationContext() {
        return generationContext;
    }

    public void setGenerationContext(GenerationContext generationContext) {
        this.generationContext = generationContext;
    }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public static class GenerationContext {
        @JsonProperty("based_on_interaction")
        private String basedOnInteraction;
        
        @JsonProperty("user_preference_signal")
        private String userPreferenceSignal;
        
        @JsonProperty("narrative_depth")
        private String narrativeDepth;

        public String getBasedOnInteraction() {
            return basedOnInteraction;
        }

        public void setBasedOnInteraction(String basedOnInteraction) {
            this.basedOnInteraction = basedOnInteraction;
        }

        public String getUserPreferenceSignal() {
            return userPreferenceSignal;
        }

        public void setUserPreferenceSignal(String userPreferenceSignal) {
            this.userPreferenceSignal = userPreferenceSignal;
        }

        public String getNarrativeDepth() {
            return narrativeDepth;
        }

        public void setNarrativeDepth(String narrativeDepth) {
            this.narrativeDepth = narrativeDepth;
        }
    }
}
