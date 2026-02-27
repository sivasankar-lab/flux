package com.example.socialmedia_poc.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Meta {
    private String category;
    @JsonProperty("meta_config")
    private MetaConfig metaConfig;

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
}
